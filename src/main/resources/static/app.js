const chunkSize = 5 * 1024 * 1024;
const tokenKey = "accessToken";

let accessToken = localStorage.getItem(tokenKey) || "";
let activeTransferController = null; // Holds AbortController for active transfers
let loadedFiles = []; // Holds list of currently loaded files for client-side search

// UI Elements
const loginPage = document.getElementById("loginPage");
const uploadPage = document.getElementById("uploadPage");
const toastAlert = document.getElementById("toastAlert");
const statusBox = document.getElementById("status");
const toastIcon = document.getElementById("toastIcon");

const progressBar = document.getElementById("progressBar");
const transfersCard = document.getElementById("transfersCard");
const transferTypeLabel = document.getElementById("transferTypeLabel");
const transferFileName = document.getElementById("transferFileName");
const transferPercent = document.getElementById("transferPercent");
const cancelTransferBtn = document.getElementById("cancelTransferBtn");

const fileList = document.getElementById("fileList");
const fileCountBadge = document.getElementById("fileCountBadge");
const emptyState = document.getElementById("emptyState");
const loadingIndicator = document.getElementById("loadingIndicator");
const searchFiles = document.getElementById("searchFiles");
const debugLog = document.getElementById("debugLog");

const dropZone = document.getElementById("dropZone");
const fileInput = document.getElementById("file");
const selectedFileInfo = document.getElementById("selectedFileInfo");
const selectedFileName = document.getElementById("selectedFileName");

// Toast utility for alerts
function showToast(message, type = "info") {
  statusBox.textContent = message;
  toastAlert.classList.remove("hidden");
  
  // Set alert icon color based on type
  if (type === "success") {
    toastIcon.style.color = "#10b981";
  } else if (type === "error") {
    toastIcon.style.color = "#ef4444";
  } else {
    toastIcon.style.color = "#818cf8";
  }
  
  // Auto hide toast after 5 seconds
  setTimeout(() => {
    toastAlert.classList.add("hidden");
  }, 5000);
}

function getToken() {
  accessToken = localStorage.getItem(tokenKey) || "";
  return accessToken;
}

function setPage() {
  const activeToken = getToken();
  loginPage.classList.toggle("hidden", Boolean(activeToken));
  uploadPage.classList.toggle("hidden", !activeToken);
  
  // Hide debug panel on login page to keep the form clean and prevent overlay issues
  const debugPanel = document.querySelector(".debug-panel");
  if (debugPanel) {
    debugPanel.classList.toggle("hidden", !activeToken);
  }
  
  if (activeToken) {
    showToast("Sudah terhubung. Selamat datang kembali!", "success");
    fetchMyFiles();
  }
}

function clearSession(message) {
  accessToken = "";
  localStorage.removeItem(tokenKey);
  localStorage.removeItem("token");
  progressBar.style.width = "0%";
  transfersCard.classList.add("hidden");
  fileList.innerHTML = "";
  setPage();
  if (message) showToast(message, "error");
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
  if (!token || token.split(".").length !== 3) return null;
  try {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64).split("").map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2)).join("")
    );
    const payload = JSON.parse(json);
    return {
      sub: payload.sub,
      userId: payload.UserId,
      iat: payload.iat,
      exp: payload.exp
    };
  } catch (error) {
    return { error: error.message };
  }
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

function headersForDebug(headers) {
  const result = {};
  Object.entries(headers || {}).forEach(([k, v]) => {
    if (k.toLowerCase() === "authorization") {
      result[k] = "Bearer " + maskToken(String(v).replace(/^Bearer\s+/i, ""));
    } else {
      result[k] = v;
    }
  });
  return result;
}

// Drag & Drop event bindings
dropZone.addEventListener("dragover", (e) => {
  e.preventDefault();
  dropZone.classList.add("dragover");
});

dropZone.addEventListener("dragleave", () => {
  dropZone.classList.remove("dragover");
});

dropZone.addEventListener("drop", (e) => {
  e.preventDefault();
  dropZone.classList.remove("dragover");
  if (e.dataTransfer.files.length > 0) {
    fileInput.files = e.dataTransfer.files;
    updateSelectedFileUI();
  }
});

fileInput.addEventListener("change", updateSelectedFileUI);

function updateSelectedFileUI() {
  const file = fileInput.files[0];
  if (file) {
    selectedFileName.textContent = `${file.name} (${formatBytes(file.size)})`;
    selectedFileInfo.classList.remove("hidden");
  } else {
    selectedFileInfo.classList.add("hidden");
  }
}

function formatBytes(bytes, decimals = 2) {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

// AUTHENTICATION
async function handleLogin(event) {
  event.preventDefault();
  showToast("Melakukan login...", "info");

  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  try {
    const response = await debugFetch("login", "/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });

    if (!response.ok) {
      showToast("Email atau password salah.", "error");
      return;
    }

    const data = await response.json();
    accessToken = data.accessToken || "";
    
    if (!accessToken) {
      showToast("Gagal mengambil token.", "error");
      return;
    }

    localStorage.setItem(tokenKey, accessToken);
    setPage();
  } catch (err) {
    showToast("Koneksi gagal atau server down.", "error");
  }
}

function handleLogout() {
  accessToken = "";
  localStorage.removeItem(tokenKey);
  localStorage.removeItem("token");
  progressBar.style.width = "0%";
  transfersCard.classList.add("hidden");
  fileInput.value = "";
  selectedFileInfo.classList.add("hidden");
  setPage();
  showToast("Berhasil logout.", "success");
}

// UPLOADING FLOW
async function handleUpload(event) {
  event.preventDefault();
  const file = fileInput.files[0];
  const activeToken = getToken();

  if (!activeToken) {
    clearSession("Sesi login berakhir. Silakan login kembali.");
    return;
  }

  if (!file) {
    showToast("Silakan pilih file terlebih dahulu.", "info");
    return;
  }

  showToast("Menginisialisasi sesi upload...", "info");
  
  // Set up UI for active transfer progress
  progressBar.style.width = "0%";
  transferTypeLabel.textContent = "UPLOAD";
  transferTypeLabel.className = "badge";
  transferFileName.textContent = file.name;
  transferPercent.textContent = "0%";
  transfersCard.classList.remove("hidden");
  cancelTransferBtn.classList.add("hidden"); // Disable active chunk-based upload cancelation to stay secure

  try {
    const initResponse = await debugFetch("init-upload", "/api/files/init", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ fileName: file.name, totalSize: file.size })
    });

    if (initResponse.status === 401 || initResponse.status === 403) {
      clearSession("Sesi Anda ditolak. Silakan login kembali.");
      return;
    }

    if (!initResponse.ok) {
      showToast("Gagal inisialisasi upload.", "error");
      transfersCard.classList.add("hidden");
      return;
    }

    const initData = await initResponse.json();
    const fileId = initData.id;
    const totalChunks = Math.ceil(file.size / chunkSize);

    for (let i = 0; i < totalChunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(file.size, start + chunkSize);
      const form = new FormData();
      form.append("file", file.slice(start, end), "chunk-" + i);

      showToast(`Mengunggah bagian ${i + 1} dari ${totalChunks}...`, "info");

      const chunkResponse = await fetch(`/api/files/${fileId}/chunks/${i}`, {
        method: "POST",
        headers: authHeaders(),
        body: form
      });

      if (!chunkResponse.ok) {
        showToast(`Gagal mengunggah bagian ke-${i + 1}`, "error");
        transfersCard.classList.add("hidden");
        return;
      }

      const percent = Math.round(((i + 1) / totalChunks) * 100);
      progressBar.style.width = percent + "%";
      transferPercent.textContent = percent + "%";
    }

    showToast("File berhasil diunggah!", "success");
    fileInput.value = "";
    selectedFileInfo.classList.add("hidden");
    
    // Hide transfer progress card after 3 seconds
    setTimeout(() => {
      transfersCard.classList.add("hidden");
    }, 3000);

    fetchMyFiles();
  } catch (err) {
    showToast("Koneksi terputus saat mengunggah.", "error");
    transfersCard.classList.add("hidden");
  }
}

// DELETION FLOW
async function handleDelete(fileId) {
  if (!confirm("Apakah Anda yakin ingin menghapus berkas ini?")) return;
  const activeToken = getToken();

  if (!activeToken) {
    clearSession("Login diperlukan.");
    return;
  }

  showToast("Menghapus file...", "info");

  try {
    const response = await debugFetch("delete-file", `/api/files/${fileId}`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (response.status === 401 || response.status === 403) {
      clearSession("Akses ditolak.");
      return;
    }

    if (!response.ok) {
      showToast("Gagal menghapus file.", "error");
      return;
    }

    showToast("File berhasil dihapus dari cloud.", "success");
    fetchMyFiles();
  } catch (err) {
    showToast("Kesalahan jaringan saat menghapus.", "error");
  }
}

// REACTIVE DOWNLOAD STREAMING FLOW WITH DYNAMIC PROGRESS AND ACTIVE CANCELATION
async function handleDownload(fileId, fileName, fileSize) {
  const activeToken = getToken();
  if (!activeToken) {
    clearSession("Sesi login diperlukan.");
    return;
  }

  showToast("Menginisialisasi sesi unduhan...", "info");

  // Reset transfer card
  progressBar.style.width = "0%";
  transferTypeLabel.textContent = "DOWNLOAD";
  transferTypeLabel.className = "badge download-badge";
  transferFileName.textContent = fileName;
  transferPercent.textContent = "0%";
  transfersCard.classList.remove("hidden");
  cancelTransferBtn.classList.remove("hidden");

  // Create AbortController to support cancellation
  activeTransferController = new AbortController();
  const { signal } = activeTransferController;

  // Bind cancel action button click
  cancelTransferBtn.onclick = async () => {
    if (activeTransferController) {
      activeTransferController.abort();
      showToast("Proses unduhan dibatalkan.", "info");
      transfersCard.classList.add("hidden");
      
      // Cancel the session on the backend asynchronously
      try {
        await fetch(`/api/files/download/${fileId}/cancel`, {
          method: "POST",
          headers: authHeaders()
        });
      } catch (e) {
        // ignore cancellation failure
      }
    }
  };

  try {
    // 1. Initialize the Download Session on R2DBC Backend
    const initResponse = await debugFetch("init-download", "/api/files/download/init", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ fileId }),
      signal
    });

    if (!initResponse.ok) {
      showToast("Unduhan gagal diinisialisasi.", "error");
      transfersCard.classList.add("hidden");
      return;
    }

    // 2. Start pulling the stream with custom Auth headers
    showToast("Memulai aliran unduhan...", "info");
    const streamResponse = await fetch(`/api/files/download/${fileId}/stream`, {
      method: "GET",
      headers: authHeaders(),
      signal
    });

    if (!streamResponse.ok) {
      showToast("Gagal mengambil stream file.", "error");
      transfersCard.classList.add("hidden");
      return;
    }

    const reader = streamResponse.body.getReader();
    const chunks = [];
    let receivedBytes = 0;

    while (true) {
      const { done, value } = await reader.read();
      
      if (done) {
        break;
      }

      chunks.push(value);
      receivedBytes += value.length;

      // Update progress bar
      if (fileSize > 0) {
        const percent = Math.round((receivedBytes / fileSize) * 100);
        progressBar.style.width = percent + "%";
        transferPercent.textContent = `${percent}% (${formatBytes(receivedBytes)} / ${formatBytes(fileSize)})`;
      } else {
        transferPercent.textContent = formatBytes(receivedBytes);
      }
    }

    showToast("Merakit file...", "info");
    const blob = new Blob(chunks, { type: "application/octet-stream" });
    const url = window.URL.createObjectURL(blob);
    
    // Trigger client-side browser save
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    
    // Clean up
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);

    showToast("File berhasil diunduh!", "success");
    setTimeout(() => {
      transfersCard.classList.add("hidden");
    }, 2000);

  } catch (err) {
    if (err.name === "AbortError") {
      console.log("Download stream aborted by user request.");
    } else {
      showToast("Unduhan gagal: koneksi bermasalah.", "error");
      transfersCard.classList.add("hidden");
    }
  } finally {
    activeTransferController = null;
  }
}

// FETCH FILE LISTINGS
async function fetchMyFiles() {
  await fetchFiles("/api/files", "get-all-user");
}

async function fetchFiles(url, label) {
  const activeToken = getToken();
  if (!activeToken) {
    clearSession("Masuk ke sistem diperlukan.");
    return;
  }

  // Set visual loading state
  loadingIndicator.classList.remove("hidden");
  emptyState.classList.add("hidden");
  fileList.innerHTML = "";
  fileCountBadge.textContent = "0 file";

  try {
    const response = await debugFetch(label, url, {
      method: "GET",
      headers: authHeaders()
    });

    loadingIndicator.classList.add("hidden");

    if (response.status === 401 || response.status === 403) {
      clearSession("Akses kedaluwarsa.");
      return;
    }

    if (!response.ok) {
      showToast("Gagal mengambil daftar file.", "error");
      emptyState.classList.remove("hidden");
      return;
    }

    const files = await response.json();
    loadedFiles = Array.isArray(files) ? files : [];

    renderFiles(loadedFiles);
  } catch (err) {
    loadingIndicator.classList.add("hidden");
    emptyState.classList.remove("hidden");
    showToast("Kesalahan jaringan saat memuat daftar.", "error");
  }
}

// Client-side search and render helper
function renderFiles(files) {
  fileList.innerHTML = "";
  
  if (files.length === 0) {
    emptyState.classList.remove("hidden");
    fileCountBadge.textContent = "0 file";
    return;
  }

  emptyState.classList.add("hidden");
  fileCountBadge.textContent = `${files.length} file`;

  files.forEach((f) => {
    const card = document.createElement("div");
    card.className = "file-card";
    
    // Shorten title if too long
    const shortName = f.originalFileName || "Berkas Tidak Bernama";
    const dateFormatted = f.createdAt ? new Date(f.createdAt).toLocaleString() : "-";
    const displaySize = formatBytes(f.size || 0);

    card.innerHTML = `
      <div class="file-info-header">
        <div class="file-icon-wrapper">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
        </div>
        <div class="file-meta-text">
          <div class="file-card-title" title="${shortName}">${shortName}</div>
          <div class="file-card-size">${displaySize}</div>
          <div class="file-card-date">${dateFormatted}</div>
        </div>
      </div>
      <div class="file-card-actions">
        <button class="btn-download-action" data-id="${f.id}" data-name="${f.originalFileName}" data-size="${f.size}">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>
          </svg>
          <span>Download</span>
        </button>
        <button class="btn-delete-action" data-id="${f.id}" title="Hapus File">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/>
          </svg>
        </button>
      </div>
    `;

    // Dynamic action event mapping
    card.querySelector(".btn-download-action").onclick = () => {
      handleDownload(f.id, f.originalFileName, f.size);
    };

    card.querySelector(".btn-delete-action").onclick = () => {
      handleDelete(f.id);
    };

    fileList.appendChild(card);
  });
}

// Client-side instant filter input
searchFiles.addEventListener("input", (e) => {
  const query = e.target.value.toLowerCase();
  const filtered = loadedFiles.filter(f => 
    (f.originalFileName || "").toLowerCase().includes(query)
  );
  renderFiles(filtered);
});

// INITIAL EVENT BINDINGS
document.getElementById("loginForm").addEventListener("submit", handleLogin);
document.getElementById("logoutButton").addEventListener("click", handleLogout);
document.getElementById("uploadForm").addEventListener("submit", handleUpload);
document.getElementById("getAllButton").addEventListener("click", fetchMyFiles);
document.getElementById("getAllAdminButton").addEventListener("click", () => fetchFiles("/api/files/admin", "get-all-admin"));
document.getElementById("clearDebugButton").addEventListener("click", () => {
  debugLog.textContent = "";
});

// Run init checks
setPage();
