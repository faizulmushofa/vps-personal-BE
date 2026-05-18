const chunkSize = 5 * 1024 * 1024;
const tokenKey = "accessToken";

let accessToken = localStorage.getItem(tokenKey) || "";

const loginPage = document.getElementById("loginPage");
const uploadPage = document.getElementById("uploadPage");
const statusBox = document.getElementById("status");
const progressBar = document.getElementById("progressBar");
const debugLog = document.getElementById("debugLog");

function setStatus(message) {
  statusBox.textContent = message;
}

function getToken() {
  accessToken = localStorage.getItem(tokenKey) || "";
  return accessToken;
}

function setPage(message) {
  const activeToken = getToken();
  loginPage.classList.toggle("hidden", Boolean(activeToken));
  uploadPage.classList.toggle("hidden", !activeToken);
  setStatus(message || (activeToken ? "Sudah login. Pilih file untuk upload." : "Silakan login dulu."));
}

function clearSession(message) {
  accessToken = "";
  localStorage.removeItem(tokenKey);
  localStorage.removeItem("token");
  progressBar.style.width = "0%";
  setPage(message);
}

function authHeaders(extra) {
  const activeToken = getToken();
  return Object.assign({ Authorization: "Bearer " + activeToken }, extra || {});
}

function maskToken(token) {
  if (!token) return "(kosong)";
  if (token.length <= 16) return token.slice(0, 4) + "...";
  return token.slice(0, 12) + "..." + token.slice(-8);
}

function decodeJwtPayload(token) {
  if (!token || token.split(".").length !== 3) {
    return null;
  }

  try {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64)
        .split("")
        .map((char) => "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    const payload = JSON.parse(json);

    return {
      sub: payload.sub,
      userId: payload.UserId,
      iat: payload.iat,
      exp: payload.exp,
      expLocal: payload.exp ? new Date(payload.exp * 1000).toLocaleString() : null,
      expiredNow: payload.exp ? Date.now() >= payload.exp * 1000 : null
    };
  } catch (error) {
    return { error: error.message };
  }
}

function headersForDebug(headers) {
  const result = {};

  Object.entries(headers || {}).forEach(([key, value]) => {
    if (key.toLowerCase() === "authorization") {
      result[key] = "Bearer " + maskToken(String(value).replace(/^Bearer\s+/i, ""));
      return;
    }

    result[key] = value;
  });

  return result;
}

function appendDebug(data) {
  const line = JSON.stringify(data, null, 2);
  debugLog.textContent += (debugLog.textContent ? "\n\n" : "") + line;
  debugLog.scrollTop = debugLog.scrollHeight;
  console.log("[request-debug]", data);
}

async function debugFetch(label, url, options) {
  const startedAt = new Date();
  const requestHeaders = options.headers || {};

  appendDebug({
    label,
    phase: "request",
    at: startedAt.toISOString(),
    method: options.method || "GET",
    url,
    tokenInStorage: maskToken(getToken()),
    tokenLength: getToken().length,
    tokenPayload: decodeJwtPayload(getToken()),
    headers: headersForDebug(requestHeaders)
  });

  try {
    const response = await fetch(url, options);
    const responseText = await response.clone().text();

    appendDebug({
      label,
      phase: "response",
      at: new Date().toISOString(),
      durationMs: Date.now() - startedAt.getTime(),
      status: response.status,
      ok: response.ok,
      contentType: response.headers.get("content-type"),
      authDebug: response.headers.get("x-auth-debug"),
      body: responseText.slice(0, 1000)
    });

    return response;
  } catch (error) {
    appendDebug({
      label,
      phase: "network-error",
      at: new Date().toISOString(),
      durationMs: Date.now() - startedAt.getTime(),
      message: error.message
    });
    throw error;
  }
}

async function readError(response) {
  const text = await response.text();
  return text || response.status + " " + response.statusText;
}

async function handleLogin(event) {
  event.preventDefault();
  setStatus("Login...");

  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const response = await debugFetch("login", "/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password })
  });

  let data = {};
  try {
    data = await response.json();
  } catch (error) {
    setStatus("Response login bukan JSON.");
    return;
  }

  if (!response.ok) {
    setStatus("Login gagal.");
    return;
  }

  accessToken = data.accessToken || "";
  if (!accessToken) {
    setStatus("Login berhasil, tapi access token tidak ditemukan di response.");
    return;
  }

  localStorage.removeItem("token");
  localStorage.setItem(tokenKey, accessToken);
  setPage();
}

function handleLogout() {
  accessToken = "";
  localStorage.removeItem(tokenKey);
  localStorage.removeItem("token");
  progressBar.style.width = "0%";
  document.getElementById("file").value = "";
  setPage();
}

async function handleUpload(event) {
  event.preventDefault();

  const file = document.getElementById("file").files[0];
  const activeToken = getToken();

  if (!activeToken) {
    clearSession("Sesi login tidak ditemukan. Login dulu.");
    return;
  }

  if (!file) {
    setStatus("Pilih file dulu.");
    return;
  }

  setStatus("Membuat sesi upload...");
  progressBar.style.width = "0%";

  const initResponse = await debugFetch("init-upload", "/api/files/init", {
    method: "POST",
    headers: authHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ fileName: file.name, totalSize: file.size })
  });

  if (initResponse.status === 401 || initResponse.status === 403) {
    setStatus("Backend menolak access token saat membuat sesi upload. Status: " + initResponse.status);
    return;
  }

  if (!initResponse.ok) {
    setStatus("Gagal membuat sesi upload: " + await readError(initResponse));
    return;
  }

  const init = await initResponse.json();
  const totalChunks = Math.ceil(file.size / chunkSize);

  for (let i = 0; i < totalChunks; i++) {
    const start = i * chunkSize;
    const end = Math.min(file.size, start + chunkSize);
    const form = new FormData();
    form.append("file", file.slice(start, end), "chunk-" + i);

    setStatus("Mengirim chunk " + (i + 1) + " dari " + totalChunks + "...");

    const chunkResponse = await debugFetch("upload-chunk-" + i, "/api/files/" + init.id + "/chunks/" + i, {
      method: "POST",
      headers: authHeaders(),
      body: form
    });

    if (chunkResponse.status === 401 || chunkResponse.status === 403) {
      setStatus("Backend menolak access token saat upload chunk " + (i + 1) + ". Status: " + chunkResponse.status);
      return;
    }

    if (!chunkResponse.ok) {
      setStatus("Upload gagal di chunk " + (i + 1) + ": " + await readError(chunkResponse));
      return;
    }

    progressBar.style.width = Math.round(((i + 1) / totalChunks) * 100) + "%";
  }

  setStatus("Upload selesai. File ID: " + init.id);
}

document.getElementById("loginForm").addEventListener("submit", handleLogin);
document.getElementById("logoutButton").addEventListener("click", handleLogout);
document.getElementById("uploadForm").addEventListener("submit", handleUpload);
document.getElementById("clearDebugButton").addEventListener("click", () => {
  debugLog.textContent = "";
});

setPage();
