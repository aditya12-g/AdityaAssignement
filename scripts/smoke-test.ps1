param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$VerifyRestart
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)
$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 10)
$script:token = $null
$script:checks = 0
$productId = $null
$composeFile = Join-Path (Split-Path $PSScriptRoot -Parent) "docker-compose.yml"

function Invoke-Api {
    param([string]$Method, [string]$Path, [int]$ExpectedStatus, $Body = $null)
    $message = New-Object System.Net.Http.HttpRequestMessage(
        (New-Object System.Net.Http.HttpMethod($Method)),
        ($BaseUrl.TrimEnd("/") + $Path))
    if ($script:token) {
        $message.Headers.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $script:token)
    }
    if ($null -ne $Body) {
        $message.Content = New-Object System.Net.Http.StringContent(
            ($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, "application/json")
    }
    try {
        $response = $client.SendAsync($message).GetAwaiter().GetResult()
        try {
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ([int]$response.StatusCode -ne $ExpectedStatus) {
                throw "$Method $Path expected $ExpectedStatus, received $([int]$response.StatusCode): $content"
            }
            $script:checks++
            Write-Host "PASS $Method $Path ($ExpectedStatus)"
            if ($content -and $response.Content.Headers.ContentType.MediaType -match "(^application/json$|\+json$)") {
                return ($content | ConvertFrom-Json)
            }
            return $content
        } finally {
            $response.Dispose()
        }
    } finally {
        $message.Dispose()
    }
}

function Assert-Value {
    param($Actual, $Expected, [string]$Description)
    if ($Actual -ne $Expected) {
        throw "$Description expected '$Expected', received '$Actual'"
    }
    $script:checks++
    Write-Host "PASS $Description"
}

function Register-AndLogin {
    param([string]$Username, [string]$Email, [string]$Role)
    $script:token = $null
    $registration = Invoke-Api POST "/api/auth/register" 202 @{
        username = $Username; email = $Email; password = "Strong!123"; userType = $Role
    }
    if (-not $registration.developmentOtp) {
        throw "This smoke test requires the local dev profile with development OTP responses enabled."
    }
    $null = Invoke-Api POST "/api/auth/login" 401 @{ email = $Email; password = "Strong!123" }
    $null = Invoke-Api POST "/api/auth/verify-otp" 200 @{
        email = $Email; otp = $registration.developmentOtp
    }
    $login = Invoke-Api POST "/api/auth/login" 200 @{ email = $Email; password = "Strong!123" }
    $script:token = $login.token
}

try {
    $health = Invoke-Api GET "/actuator/health" 200
    Assert-Value $health.status "UP" "Application and database health"
    $null = Invoke-Api GET "/" 200
    $null = Invoke-Api GET "/v3/api-docs" 200
    $null = Invoke-Api GET "/api/products" 401
    Register-AndLogin "smoke$suffix" "smoke_$suffix@example.com" "Standard User"
    $dashboard = Invoke-Api GET "/api/dashboard" 200
    Assert-Value $dashboard.message "Welcome smoke$suffix" "Dashboard welcome"
    $null = Invoke-Api GET "/api/settings" 403
    $null = Invoke-Api POST "/api/products" 400 @{ name = "Invalid"; price = -1; stock = 0 }
    $created = Invoke-Api POST "/api/products" 201 @{
        name = "Smoke $suffix"; description = "Temporary smoke-test product"; price = 12.50; stock = 3
    }
    $productId = $created.id
    $null = Invoke-Api PUT "/api/products/$productId" 200 @{
        name = "Smoke $suffix"; description = "Updated"; price = 15.75; stock = 4
    }
    $read = Invoke-Api GET "/api/products/$productId" 200
    Assert-Value $read.price 15.75 "Decimal price round trip"
    Assert-Value $read.stock 4 "Updated stock round trip"
    $page = Invoke-Api GET "/api/products?page=0&size=1&sort=id,desc" 200
    Assert-Value $page.page.size 1 "Pagination metadata"

    if ($VerifyRestart) {
        Write-Host "Restarting only the Northstar backend and PostgreSQL services..."
        docker compose -f $composeFile restart postgres backend
        if ($LASTEXITCODE -ne 0) { throw "Compose restart failed" }
        docker compose -f $composeFile up -d --wait backend
        if ($LASTEXITCODE -ne 0) { throw "Stack did not become healthy after restart" }
        $null = Invoke-Api GET "/api/dashboard" 401
        Register-AndLogin "after$suffix" "after_$suffix@example.com" "Standard User"
        $persisted = Invoke-Api GET "/api/products/$productId" 200
        Assert-Value $persisted.price 15.75 "PostgreSQL product survives service restart"
    }

    $null = Invoke-Api DELETE "/api/products/$productId" 204
    $null = Invoke-Api GET "/api/products/$productId" 404
    $productId = $null
    $null = Invoke-Api POST "/api/auth/logout" 200
    $null = Invoke-Api GET "/api/dashboard" 401
    Register-AndLogin "admin$suffix" "admin_$suffix@example.com" "Administrator"
    $null = Invoke-Api GET "/api/settings" 200
    $null = Invoke-Api POST "/api/auth/logout" 200
    Write-Host "SUCCESS: $script:checks checks passed."
} finally {
    if ($null -ne $productId -and $script:token) {
        try {
            $null = Invoke-Api DELETE "/api/products/$productId" 204
        } catch {
            Write-Warning "Could not clean up smoke-test product $productId : $_"
        }
    }
    $client.Dispose()
}
