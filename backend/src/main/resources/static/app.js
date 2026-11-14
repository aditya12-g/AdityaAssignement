const tokenKey = "northstar.session";
const pageSize = 8;
let currentPage = 0;
let totalPages = 0;
let editingProductId = null;
let pendingEmail = "";

const $ = (selector) => document.querySelector(selector);
const authView = $("#auth-view");
const appView = $("#app-view");
const loginForm = $("#login-form");
const registerForm = $("#register-form");
const otpForm = $("#otp-form");
const productForm = $("#product-form");

function showNotice(message, isError = false) {
  const notice = $("#notice");
  notice.textContent = message;
  notice.classList.toggle("error", isError);
  notice.classList.remove("hidden");
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set("Content-Type", "application/json");
  const token = sessionStorage.getItem(tokenKey);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...options, headers });
  if (response.status === 204) return null;
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.message || `Request failed (${response.status})`);
  return result;
}

function selectAuthForm(name) {
  $("#login-tab").classList.toggle("active", name === "login");
  $("#register-tab").classList.toggle("active", name === "register");
  $("#login-tab").setAttribute("aria-selected", String(name === "login"));
  $("#register-tab").setAttribute("aria-selected", String(name === "register"));
  loginForm.classList.toggle("hidden", name !== "login");
  registerForm.classList.toggle("hidden", name !== "register");
  otpForm.classList.add("hidden");
  $("#login-error").textContent = "";
  $("#register-error").textContent = "";
}

async function loadProducts() {
  const data = await request(`/api/products?page=${currentPage}&size=${pageSize}&sort=name,asc`);
  const page = data.page || data;
  totalPages = page.totalPages;
  if (totalPages > 0 && currentPage >= totalPages) {
    currentPage = totalPages - 1;
    return loadProducts();
  }
  $("#product-count").textContent = `${page.totalElements} total`;
  $("#page-label").textContent = totalPages ? `${currentPage + 1} / ${totalPages}` : "0 / 0";
  $("#previous-page").disabled = currentPage <= 0;
  $("#next-page").disabled = currentPage + 1 >= totalPages;

  const list = $("#product-list");
  list.replaceChildren();
  $("#empty-products").classList.toggle("hidden", data.content.length !== 0);

  for (const product of data.content) {
    const row = document.createElement("article");
    row.className = "product-row";

    const info = document.createElement("div");
    const name = document.createElement("p");
    name.className = "product-name";
    name.textContent = product.name;
    const description = document.createElement("p");
    description.className = "product-description";
    description.textContent = product.description || "No description";
    info.append(name, description);

    const price = document.createElement("span");
    price.className = "product-price";
    price.textContent = new Intl.NumberFormat(undefined, { style: "currency", currency: "USD" }).format(product.price);

    const stock = document.createElement("span");
    stock.className = "product-meta";
    stock.textContent = `${product.stock} in stock`;

    const actions = document.createElement("div");
    actions.className = "row-actions";
    const edit = document.createElement("button");
    edit.className = "row-action";
    edit.type = "button";
    edit.textContent = "Edit";
    edit.addEventListener("click", () => beginEdit(product));
    const remove = document.createElement("button");
    remove.className = "row-action delete";
    remove.type = "button";
    remove.textContent = "Delete";
    remove.addEventListener("click", () => deleteProduct(product));
    actions.append(edit, remove);

    row.append(info, price, stock, actions);
    list.append(row);
  }
}

function beginEdit(product) {
  editingProductId = product.id;
  productForm.elements.name.value = product.name;
  productForm.elements.description.value = product.description || "";
  productForm.elements.price.value = product.price;
  productForm.elements.stock.value = product.stock;
  $("#product-form-title").textContent = "Edit product";
  $("#product-submit").textContent = "Save changes";
  $("#cancel-edit").classList.remove("hidden");
  productForm.scrollIntoView({ behavior: "smooth", block: "center" });
}

function cancelEdit() {
  editingProductId = null;
  productForm.reset();
  productForm.elements.stock.value = "0";
  $("#product-form-title").textContent = "Add a product";
  $("#product-submit").textContent = "Add product";
  $("#cancel-edit").classList.add("hidden");
}

async function deleteProduct(product) {
  if (!window.confirm(`Delete “${product.name}”?`)) return;
  try {
    await request(`/api/products/${product.id}`, { method: "DELETE" });
    showNotice(`${product.name} was deleted.`);
    await loadProducts();
  } catch (error) {
    showNotice(error.message, true);
  }
}

async function openWorkspace(auth) {
  authView.classList.add("hidden");
  appView.classList.remove("hidden");
  $("#welcome-message").textContent = `Welcome ${auth.username}`;
  $("#role-badge").textContent = auth.role;
  $("#settings-panel").classList.toggle("hidden", auth.role !== "Administrator");
  if (auth.role === "Administrator") {
    try {
      const settings = await request("/api/settings");
      $("#settings-message").textContent = settings.message;
    } catch (error) {
      $("#settings-message").textContent = error.message;
    }
  }
  try {
    await loadProducts();
  } catch (error) {
    showNotice(error.message, true);
  }
}

$("#login-tab").addEventListener("click", () => selectAuthForm("login"));
$("#register-tab").addEventListener("click", () => selectAuthForm("register"));

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#login-error").textContent = "";
  $("#login-error").classList.remove("success");
  const form = new FormData(loginForm);
  try {
    const auth = await request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email: form.get("email"), password: form.get("password") })
    });
    sessionStorage.setItem(tokenKey, auth.token);
    await openWorkspace(auth);
  } catch (error) {
    $("#login-error").textContent = error.message;
  }
});

registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#register-error").textContent = "";
  const form = new FormData(registerForm);
  pendingEmail = String(form.get("email"));
  try {
    const result = await request("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        username: form.get("username"),
        email: pendingEmail,
        password: form.get("password"),
        userType: form.get("userType")
      })
    });
    $("#otp-email").textContent = pendingEmail;
    $("#otp-hint").textContent = result.developmentOtp
      ? `Development code: ${result.developmentOtp} (expires in 5 minutes)`
      : "The code expires in 5 minutes.";
    registerForm.classList.add("hidden");
    otpForm.classList.remove("hidden");
  } catch (error) {
    $("#register-error").textContent = error.message;
  }
});

otpForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#otp-error").textContent = "";
  const form = new FormData(otpForm);
  try {
    await request("/api/auth/verify-otp", {
      method: "POST",
      body: JSON.stringify({ email: pendingEmail, otp: form.get("otp") })
    });
    otpForm.reset();
    selectAuthForm("login");
    loginForm.elements.email.value = pendingEmail;
    $("#login-error").textContent = "Account verified. Sign in to continue.";
    $("#login-error").classList.add("success");
  } catch (error) {
    $("#otp-error").textContent = error.message;
  }
});

productForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#product-error").textContent = "";
  const form = new FormData(productForm);
  const product = {
    name: form.get("name"),
    description: form.get("description"),
    price: Number(form.get("price")),
    stock: Number(form.get("stock"))
  };
  try {
    await request(editingProductId ? `/api/products/${editingProductId}` : "/api/products", {
      method: editingProductId ? "PUT" : "POST",
      body: JSON.stringify(product)
    });
    showNotice(editingProductId ? "Product updated." : "Product added.");
    cancelEdit();
    currentPage = 0;
    await loadProducts();
  } catch (error) {
    $("#product-error").textContent = error.message;
  }
});

$("#cancel-edit").addEventListener("click", cancelEdit);
$("#previous-page").addEventListener("click", async () => {
  if (currentPage > 0) {
    currentPage--;
    await loadProducts();
  }
});
$("#next-page").addEventListener("click", async () => {
  if (currentPage + 1 < totalPages) {
    currentPage++;
    await loadProducts();
  }
});

$("#logout-button").addEventListener("click", async () => {
  try {
    const result = await request("/api/auth/logout", { method: "POST" });
    sessionStorage.removeItem(tokenKey);
    appView.classList.add("hidden");
    authView.classList.remove("hidden");
    selectAuthForm("login");
    $("#login-error").textContent = result.message;
    $("#login-error").classList.add("success");
  } catch (error) {
    showNotice(error.message, true);
  }
});

if (sessionStorage.getItem(tokenKey)) {
  request("/api/auth/me")
    .then(openWorkspace)
    .catch(() => sessionStorage.removeItem(tokenKey));
}
