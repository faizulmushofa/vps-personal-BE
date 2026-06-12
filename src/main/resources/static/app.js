const chunkSize = 5 * 1024 * 1024;
const tokenKey = "accessToken";

let accessToken = localStorage.getItem(tokenKey) || "";
let activeTransferController = null; // Holds AbortController for active transfers
let loadedFiles = []; // Holds list of currently loaded files for client-side search
let currentViewIsShared = false; // Tracks if current view is shared files
let userUsedBytes = 0;
let userQuotaBytes = 0;
let currentTargetUserId = null;
let adminUsersList = [];
let googleCodeClient = null;

// UI Elements
const loginPage = document.getElementById("loginPage");
const registerPage = document.getElementById("registerPage");
const otpPage = document.getElementById("otpPage");
const forgotPasswordPage = document.getElementById("forgotPasswordPage");
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
  registerPage.classList.add("hidden");
  otpPage.classList.add("hidden");
  forgotPasswordPage.classList.add("hidden");
  uploadPage.classList.toggle("hidden", !activeToken);
  
  // Hide debug panel on login page to keep the form clean and prevent overlay issues
  const debugPanel = document.querySelector(".debug-panel");
  if (debugPanel) {
    debugPanel.classList.toggle("hidden", !activeToken);
  }
  
  if (activeToken) {
    showToast("Sudah terhubung. Selamat datang kembali!", "success");
    fetchUserProfile();
    fetchMyFiles();
    fetchStorageQuota();
    probeAdminAccess();
    initGoogleGis();
  } else {
    // Reset admin UI states
    document.getElementById("adminPanelButton").classList.add("hidden");
    closeAdminDashboard();
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
      try {
        const errorData = await response.json();
        const errorMessage = errorData.message || "Email atau password salah.";
        showToast(errorMessage, "error");
        
        if (errorMessage.includes("belum aktif") || errorMessage.includes("verifikasi email")) {
          document.getElementById("otpEmail").value = email;
          loginPage.classList.add("hidden");
          otpPage.classList.remove("hidden");
        }
      } catch (parseErr) {
        showToast("Email atau password salah.", "error");
      }
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

async function handleRegister(event) {
  event.preventDefault();
  showToast("Mendaftarkan akun...", "info");

  const username = document.getElementById("regUsername").value;
  const fullName = document.getElementById("regFullName").value;
  const email = document.getElementById("regEmail").value;
  const phoneNumber = document.getElementById("regPhoneNumber").value;
  const password = document.getElementById("regPassword").value;
  const confirmPassword = document.getElementById("regConfirmPassword").value;

  if (password !== confirmPassword) {
    showToast("Password dan Konfirmasi Password tidak cocok.", "error");
    return;
  }

  try {
    const response = await debugFetch("register", "/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, fullName, email, phoneNumber, password })
    });

    if (!response.ok) {
      showToast("Gagal melakukan registrasi. Email atau Username mungkin sudah digunakan.", "error");
      return;
    }

    showToast("Registrasi berhasil! Kode OTP verifikasi telah dikirim ke email Anda.", "success");
    
    // Set email on OTP page
    document.getElementById("otpEmail").value = email;
    
    // Reset form fields
    document.getElementById("registerForm").reset();
    
    // Switch to OTP page
    registerPage.classList.add("hidden");
    otpPage.classList.remove("hidden");
  } catch (err) {
    showToast("Koneksi gagal atau server down.", "error");
  }
}

async function handleVerifyOtp(event) {
  event.preventDefault();
  showToast("Memverifikasi OTP...", "info");

  const email = document.getElementById("otpEmail").value;
  const otp = document.getElementById("otpCode").value;

  try {
    const response = await debugFetch("verifyOtp", "/api/auth/verify-registration", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, otp })
    });

    if (!response.ok) {
      showToast("Verifikasi gagal. Kode OTP mungkin salah atau kadaluarsa.", "error");
      return;
    }

    showToast("Verifikasi berhasil! Akun Anda telah aktif. Silakan masuk.", "success");
    document.getElementById("otpForm").reset();
    otpPage.classList.add("hidden");
    loginPage.classList.remove("hidden");
  } catch (err) {
    showToast("Koneksi gagal atau server down.", "error");
  }
}

async function handleForgotPassword(event) {
  event.preventDefault();
  
  const step2Fields = document.getElementById("fpStep2Fields");
  const emailInput = document.getElementById("fpEmail");
  const submitText = document.getElementById("fpSubmitText");
  const subHeading = document.getElementById("fpSubHeading");
  
  const email = emailInput.value;
  const isStep1 = step2Fields.classList.contains("hidden");
  
  if (isStep1) {
    showToast("Mengirim kode OTP pemulihan...", "info");
    try {
      const response = await debugFetch("requestFpOtp", "/api/auth/forgot-password/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email })
      });
      
      if (!response.ok) {
        showToast("Gagal mengirim OTP. Email mungkin tidak terdaftar atau belum aktif.", "error");
        return;
      }
      
      showToast("OTP pemulihan berhasil dikirim ke email Anda.", "success");
      step2Fields.classList.remove("hidden");
      emailInput.setAttribute("readonly", "true");
      submitText.textContent = "Setel Ulang Password";
      subHeading.textContent = "Masukkan kode OTP pemulihan dan password baru Anda";
      
      document.getElementById("fpOtpCode").setAttribute("required", "true");
      document.getElementById("fpNewPassword").setAttribute("required", "true");
      document.getElementById("fpConfirmNewPassword").setAttribute("required", "true");
    } catch (err) {
      showToast("Koneksi gagal atau server down.", "error");
    }
  } else {
    const otp = document.getElementById("fpOtpCode").value;
    const newPassword = document.getElementById("fpNewPassword").value;
    const confirmNewPassword = document.getElementById("fpConfirmNewPassword").value;
    
    if (newPassword !== confirmNewPassword) {
      showToast("Password Baru dan Konfirmasi Password tidak cocok.", "error");
      return;
    }
    
    showToast("Mengubah password...", "info");
    try {
      const response = await debugFetch("resetPassword", "/api/auth/forgot-password/reset", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, otp, newPassword })
      });
      
      if (!response.ok) {
        showToast("Gagal mengatur ulang password. Kode OTP mungkin salah atau kadaluarsa.", "error");
        return;
      }
      
      showToast("Kata sandi berhasil diperbarui! Silakan masuk kembali.", "success");
      
      document.getElementById("forgotPasswordForm").reset();
      step2Fields.classList.add("hidden");
      emailInput.removeAttribute("readonly");
      submitText.textContent = "Kirim Kode OTP";
      subHeading.textContent = "Masukkan email Anda untuk menerima kode pemulihan";
      
      document.getElementById("fpOtpCode").removeAttribute("required");
      document.getElementById("fpNewPassword").removeAttribute("required");
      document.getElementById("fpConfirmNewPassword").removeAttribute("required");
      
      forgotPasswordPage.classList.add("hidden");
      loginPage.classList.remove("hidden");
    } catch (err) {
      showToast("Koneksi gagal atau server down.", "error");
    }
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

  // Clear profile widgets
  const greeting = document.getElementById("userGreeting");
  if (greeting) greeting.textContent = "Dashboard Cloud";
  
  const fullName = document.getElementById("userFullName");
  if (fullName) fullName.textContent = "";
  
  const email = document.getElementById("userEmail");
  if (email) email.textContent = "";
  
  const avatar = document.getElementById("userAvatar");
  if (avatar) {
    avatar.src = "";
    avatar.classList.add("hidden");
  }

  setPage();
  showToast("Berhasil logout.", "success");
}

async function fetchUserProfile() {
  const activeToken = getToken();
  if (!activeToken) return;

  try {
    const response = await debugFetch("get-user-profile", "/api/users/me", {
      method: "GET",
      headers: authHeaders()
    });

    if (response.ok) {
      const user = await response.json();
      
      const displayName = user.fullName || user.username;
      
      const greeting = document.getElementById("userGreeting");
      if (greeting) greeting.textContent = `Selamat datang, ${displayName}!`;
      
      const fullName = document.getElementById("userFullName");
      if (fullName) fullName.textContent = displayName;
      
      const emailText = document.getElementById("userEmail");
      if (emailText) emailText.textContent = user.email;

      const avatar = document.getElementById("userAvatar");
      if (avatar) {
        if (user.avatarUrl) {
          avatar.src = user.avatarUrl;
          avatar.classList.remove("hidden");
        } else {
          avatar.src = "";
          avatar.classList.add("hidden");
        }
      }
    }
  } catch (error) {
    console.error("Gagal memuat profil pengguna:", error);
  }
}

// GOOGLE IDENTITY SERVICES (GIS) INTEGRATION
let connectedGoogleAccounts = [];

function toggleStorageProviderSelect() {
  const provider = document.getElementById("storageProvider").value;
  const selectGroup = document.getElementById("googleDriveAccountSelectGroup");
  if (provider === "GOOGLE_DRIVE" && connectedGoogleAccounts.length > 0) {
    selectGroup.classList.remove("hidden");
  } else {
    selectGroup.classList.add("hidden");
  }
}

async function fetchSingleGoogleStorageQuota(accountId) {
  try {
    const response = await debugFetch(`get-gdrive-storage-${accountId}`, `/api/google-drive/storage?externalAccountId=${accountId}`, {
      method: "GET",
      headers: authHeaders()
    });
    if (response.ok) {
      const quota = await response.json();
      if (quota.googleDriveConnected) {
        const textEl = document.getElementById(`gdriveQuotaText-${accountId}`);
        const pctEl = document.getElementById(`gdriveQuotaPercentage-${accountId}`);
        const progressEl = document.getElementById(`gdriveQuotaBarProgress-${accountId}`);
        
        if (textEl && pctEl && progressEl) {
          const usedStr = formatBytes(quota.googleUsedBytes || 0);
          const limitStr = formatBytes(quota.googleQuotaBytes || 0);
          textEl.textContent = `${usedStr} dari ${limitStr}`;
          
          let pct = 0;
          if (quota.googleQuotaBytes > 0) {
            pct = Math.round(((quota.googleUsedBytes || 0) / quota.googleQuotaBytes) * 100);
          }
          pctEl.textContent = pct + "%";
          progressEl.style.width = pct + "%";
        }
      }
    }
  } catch (e) {
    console.error(`Failed to fetch storage quota for account ${accountId}`, e);
  }
}

async function checkGoogleConnection() {
  const activeToken = getToken();
  if (!activeToken) return;

  try {
    const response = await debugFetch("get-my-accounts", "/api/external-accounts/me", {
      headers: authHeaders()
    });

    const accountsList = document.getElementById("connectedGoogleAccountsList");
    const btnContainer = document.getElementById("googleBtnContainer");
    const statusText = document.getElementById("googleStatus");
    const selectGroup = document.getElementById("googleDriveAccountSelectGroup");
    const selectEl = document.getElementById("googleDriveAccountSelect");
    const quotaContainer = document.getElementById("googleDrivesQuotaContainer");

    if (!accountsList || !btnContainer || !statusText || !selectGroup || !selectEl || !quotaContainer) return;

    accountsList.innerHTML = "";
    quotaContainer.innerHTML = "";
    selectEl.innerHTML = "";

    if (response.ok) {
      const accounts = await response.json();
      connectedGoogleAccounts = accounts.filter(acc => acc.provider.toUpperCase() === "GOOGLE");

      if (connectedGoogleAccounts.length > 0) {
        statusText.textContent = `${connectedGoogleAccounts.length} Akun Google Terhubung`;
        statusText.style.color = "#10b981";

        connectedGoogleAccounts.forEach(account => {
          const accountId = account.id;
          const accountEmail = account.email;

          const accItem = document.createElement("div");
          accItem.style = "padding: 8px; border: 1px solid rgba(255,255,255,0.06); border-radius: 8px; background: rgba(255,255,255,0.02); display: flex; flex-direction: column; gap: 8px; margin-bottom: 4px;";
          accItem.innerHTML = `
            <div style="font-weight: 500; font-size: 0.8rem; color: var(--text-primary); display: flex; align-items: center; justify-content: space-between;">
              <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 170px;">${accountEmail}</span>
              <span style="color: #10b981; font-size: 0.7rem;">Active</span>
            </div>
            <div style="display: flex; gap: 4px;">
              <button class="primary-btn sync-btn-${accountId}" style="flex: 1; padding: 6px; font-size: 0.7rem; display: flex; align-items: center; justify-content: center; gap: 4px; background: linear-gradient(135deg, #a855f7 0%, #3b82f6 100%); border: none;">
                <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
                <span>Sync</span>
              </button>
              <button class="danger-btn disconnect-btn-${accountId}" style="flex: 1; padding: 6px; font-size: 0.7rem; display: flex; align-items: center; justify-content: center; gap: 4px; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.4); color: #ef4444;">
                <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2"><path d="M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10"/></svg>
                <span>Putus</span>
              </button>
            </div>
          `;
          accountsList.appendChild(accItem);

          accItem.querySelector(`.sync-btn-${accountId}`).onclick = () => syncGoogleDrive(accountId);
          accItem.querySelector(`.disconnect-btn-${accountId}`).onclick = () => disconnectGoogleAccount(accountId);

          const quotaDiv = document.createElement("div");
          quotaDiv.innerHTML = `
            <div style="font-size: 0.75rem; color: var(--text-secondary); margin-bottom: 4px; display: flex; align-items: center; gap: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M19.347 14.625l-4.14-7.172h-6.41l4.14 7.172h6.41zM9.544 16.125l3.205-5.553-3.205-5.553-3.205 5.553 3.205 5.553zM10.456 16.125h6.41l-3.205-5.553-3.205 5.553z"/></svg>
              GDrive: ${accountEmail}
            </div>
            <div class="quota-info" style="display: flex; justify-content: space-between; font-size: 0.8rem; font-weight: 500;">
              <span id="gdriveQuotaText-${accountId}">0 B dari 0 B</span>
              <span id="gdriveQuotaPercentage-${accountId}">0%</span>
            </div>
            <div class="progress-bar-container quota-bar-bg" style="margin-top: 4px; margin-bottom: 8px;">
              <div id="gdriveQuotaBarProgress-${accountId}" class="progress-bar-fill quota-bar-fill" style="width: 0%; background: linear-gradient(135deg, #10b981 0%, #059669 100%);"></div>
            </div>
          `;
          quotaContainer.appendChild(quotaDiv);

          const option = document.createElement("option");
          option.value = accountId;
          option.textContent = accountEmail;
          selectEl.appendChild(option);

          fetchSingleGoogleStorageQuota(accountId);
        });

        toggleStorageProviderSelect();
      } else {
        statusText.textContent = "Hubungkan akun Google Anda";
        statusText.style.color = "var(--text-secondary)";
        selectGroup.classList.add("hidden");
      }
    }

    if (googleCodeClient) {
      btnContainer.innerHTML = `
        <button id="connectGoogleBtn" class="primary-btn full-width" style="display: flex; align-items: center; justify-content: center; gap: 8px; padding: 10px; font-weight: 600; cursor: pointer;">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M12.24 10.285V13.4h6.887C18.2 15.614 15.645 18 12.24 18c-3.86 0-7-3.14-7-7s3.14-7 7-7c1.706 0 3.257.614 4.473 1.636l2.427-2.427C17.29 1.523 14.909 1 12.24 1A9.99 9.99 0 002.25 11a9.99 9.99 0 009.99 10c5.556 0 9.99-4.004 9.99-10 0-.682-.082-1.336-.237-1.715H12.24z"/>
          </svg>
          <span>Hubungkan Akun Google Baru</span>
        </button>
      `;
      document.getElementById("connectGoogleBtn").addEventListener("click", () => {
        googleCodeClient.requestCode();
      });
    } else {
      btnContainer.innerHTML = `<p style="font-size: 0.8rem; color: var(--text-secondary);">Memuat Google Identity Services...</p>`;
    }
  } catch (err) {
    console.error("Gagal memeriksa koneksi Google", err);
  }
}

async function syncGoogleDrive(externalAccountId) {
  showToast("Sinkronisasi berkas Google Drive sedang berlangsung...", "info");
  try {
    const response = await debugFetch("sync-google-drive", `/api/google-drive/sync?externalAccountId=${externalAccountId}`, {
      method: "POST",
      headers: authHeaders()
    });

    if (response.ok) {
      showToast("Sinkronisasi Google Drive selesai!", "success");
      await fetchMyFiles();
    } else {
      showToast("Gagal mensinkronisasi Google Drive.", "error");
    }
  } catch (error) {
    showToast("Koneksi bermasalah saat sinkronisasi.", "error");
  }
}

async function disconnectGoogleAccount(id) {
  if (!confirm("Apakah Anda yakin ingin memutuskan hubungan akun Google Drive Anda? File yang disinkronisasi tidak akan bisa diakses sampai dihubungkan kembali.")) {
    return;
  }

  showToast("Memutus sambungan Google...", "info");
  try {
    const response = await debugFetch("disconnect-google", `/api/external-accounts/${id}`, {
      method: "DELETE",
      headers: authHeaders()
    });

    if (response.ok) {
      showToast("Akun Google Drive berhasil diputuskan.", "success");
      await checkGoogleConnection();
      await fetchMyFiles();
    } else {
      showToast("Gagal memutus sambungan Google.", "error");
    }
  } catch (error) {
    showToast("Koneksi bermasalah saat memutus sambungan.", "error");
  }
}

async function initGoogleGis() {
  if (typeof google === "undefined" || !google.accounts) {
    setTimeout(initGoogleGis, 500);
    return;
  }

  try {
    const response = await debugFetch("get-google-client-id", "/api/external-accounts/auth-url?provider=google", {
      method: "GET",
      headers: authHeaders()
    });

    if (!response.ok) {
      console.error("Gagal mengambil Google Client ID dari backend");
      return;
    }

    const clientId = await response.text();
    if (!clientId) {
      console.warn("Google Client ID kosong");
      return;
    }

    googleCodeClient = google.accounts.oauth2.initCodeClient({
      client_id: clientId,
      scope: "https://www.googleapis.com/auth/drive email profile openid",
      ux_mode: "popup",
      callback: (authResponse) => {
        if (authResponse.code) {
          handleGoogleCodeResponse(authResponse.code);
        } else {
          showToast("Otorisasi dibatalkan.", "error");
        }
      }
    });

    // Check if user is already connected
    await checkGoogleConnection();
  } catch (error) {
    console.error("Error inisialisasi Google GIS:", error);
  }
}

async function handleGoogleCodeResponse(code) {
  showToast("Menghubungkan akun Google...", "info");
  
  const btnContainer = document.getElementById("googleBtnContainer");
  const statusText = document.getElementById("googleStatus");
  let originalBtnHtml = "";

  if (btnContainer) {
    originalBtnHtml = btnContainer.innerHTML;
    btnContainer.innerHTML = `
      <button id="connectGoogleBtn" class="primary-btn full-width" style="display: flex; align-items: center; justify-content: center; gap: 8px; padding: 10px; font-weight: 600; cursor: not-allowed; opacity: 0.7;" disabled>
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" style="animation: spin 1s linear infinite;">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" stroke-dasharray="32" stroke-dashoffset="8" fill="none"></circle>
        </svg>
        <span>Menghubungkan...</span>
      </button>
    `;
  }
  if (statusText) {
    statusText.textContent = "Menghubungkan...";
    statusText.style.color = "var(--text-secondary)";
  }

  try {
    const res = await debugFetch("init-external-account", "/api/external-accounts/init?provider=google", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ token: code })
    });

    if (res.ok) {
      showToast("Google Drive berhasil dihubungkan!", "success");
      await checkGoogleConnection();
      fetchStorageQuota();
      await fetchMyFiles();
    } else {
      let errorMsg = "Gagal menghubungkan akun Google.";
      try {
        const errorData = await res.json();
        if (errorData && errorData.message) {
          errorMsg = errorData.message;
        } else if (errorData && errorData.error) {
          errorMsg = errorData.error;
        }
      } catch (e) {}

      showToast(`Gagal: ${errorMsg}`, "error");
      if (statusText) {
        statusText.textContent = `Gagal: ${errorMsg}`;
        statusText.style.color = "#ef4444";
      }
      if (btnContainer) {
        btnContainer.innerHTML = originalBtnHtml;
        const connBtn = document.getElementById("connectGoogleBtn");
        if (connBtn && googleCodeClient) {
          connBtn.addEventListener("click", () => {
            googleCodeClient.requestCode();
          });
        }
      }
    }
  } catch (error) {
    showToast("Koneksi bermasalah saat menghubungkan akun.", "error");
    if (statusText) {
      statusText.textContent = "Koneksi bermasalah.";
      statusText.style.color = "#ef4444";
    }
    if (btnContainer) {
      btnContainer.innerHTML = originalBtnHtml;
      const connBtn = document.getElementById("connectGoogleBtn");
      if (connBtn && googleCodeClient) {
        connBtn.addEventListener("click", () => {
          googleCodeClient.requestCode();
        });
      }
    }
  }
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

  const provider = document.getElementById("storageProvider").value || "STORAGE_NODE";

  // Client-side quick quota check (only for local storage node)
  if (provider === "STORAGE_NODE" && userQuotaBytes > 0 && (userUsedBytes + file.size > userQuotaBytes)) {
    showToast(`Gagal: Ukuran file (${formatBytes(file.size)}) melebihi sisa kapasitas storage Anda!`, "error");
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
    const initUrl = provider === "GOOGLE_DRIVE" 
      ? "/api/google-drive/upload/init" 
      : "/api/files/init";

    const bodyObj = { fileName: file.name, totalSize: file.size, provider: provider };
    if (provider === "GOOGLE_DRIVE") {
      const selectVal = document.getElementById("googleDriveAccountSelect").value;
      if (!selectVal) {
        showToast("Hubungkan dan pilih akun Google Drive terlebih dahulu.", "error");
        transfersCard.classList.add("hidden");
        return;
      }
      bodyObj.externalAccountId = parseInt(selectVal);
    }

    const initResponse = await debugFetch("init-upload", initUrl, {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(bodyObj)
    });

    if (initResponse.status === 401 || initResponse.status === 403) {
      clearSession("Sesi Anda ditolak. Silakan login kembali.");
      return;
    }

    if (!initResponse.ok) {
      let errMsg = "Gagal inisialisasi upload.";
      try {
        const errData = await initResponse.json();
        if (errData && errData.message) {
          errMsg = errData.message;
        }
      } catch (e) {}
      showToast(errMsg, "error");
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

      const chunkUrl = provider === "GOOGLE_DRIVE"
        ? `/api/google-drive/upload/${fileId}/chunks/${i}`
        : `/api/files/${fileId}/chunks/${i}`;

      const chunkResponse = await fetch(chunkUrl, {
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
async function handleDelete(fileId, provider) {
  if (!confirm("Apakah Anda yakin ingin menghapus berkas ini?")) return;
  const activeToken = getToken();

  if (!activeToken) {
    clearSession("Login diperlukan.");
    return;
  }

  showToast("Menghapus file...", "info");

  try {
    const url = provider === "GOOGLE_DRIVE"
      ? `/api/google-drive/files/${fileId}`
      : `/api/files/${fileId}`;

    const response = await debugFetch("delete-file", url, {
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

    const warning = response.headers.get("X-Warning");
    if (warning) {
      showToast(`Terhapus dari dashboard, namun gagal dari Google Drive API: ${warning}`, "info");
    } else {
      showToast("File berhasil dihapus.", "success");
    }
    fetchMyFiles();
  } catch (err) {
    showToast("Kesalahan jaringan saat menghapus.", "error");
  }
}

// SHARING FLOW
async function handleShare(fileId) {
  const email = prompt("Masukkan Email User yang akan diberi akses:");
  if (!email) return;
  
  showToast("Membagikan file...", "info");
  try {
    const response = await debugFetch("share-file", `/api/files/share/${fileId}`, {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ email })
    });
    if (response.status === 401 || response.status === 403) {
      clearSession("Akses ditolak.");
      return;
    }
    if (!response.ok) {
      showToast("Gagal membagikan file.", "error");
      return;
    }
    showToast("File berhasil dibagikan!", "success");
  } catch (err) {
    showToast("Kesalahan jaringan saat membagikan.", "error");
  }
}

// REACTIVE DOWNLOAD STREAMING FLOW WITH DYNAMIC PROGRESS AND ACTIVE CANCELATION
async function handleDownload(fileId, fileName, fileSize, provider) {
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
        const cancelUrl = provider === "GOOGLE_DRIVE"
          ? `/api/google-drive/download/${fileId}/cancel`
          : `/api/files/download/${fileId}/cancel`;
        await fetch(cancelUrl, {
          method: "POST",
          headers: authHeaders()
        });
      } catch (e) {
        // ignore cancellation failure
      }
    }
  };

  try {
    const initUrl = provider === "GOOGLE_DRIVE"
      ? "/api/google-drive/download/init"
      : "/api/files/download/init";

    // 1. Initialize the Download Session on R2DBC Backend
    const initResponse = await debugFetch("init-download", initUrl, {
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

    const streamUrl = provider === "GOOGLE_DRIVE"
      ? `/api/google-drive/download/${fileId}/stream`
      : `/api/files/download/${fileId}/stream`;

    // 2. Start pulling the stream with custom Auth headers
    showToast("Memulai aliran unduhan...", "info");
    const streamResponse = await fetch(streamUrl, {
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
  await fetchFiles("/api/files", "get-all-user", false);
  fetchStorageQuota();
}

async function fetchFiles(url, label, isShared = false) {
  currentViewIsShared = isShared;
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
    const isPdf = (f.originalFileName || "").toLowerCase().endsWith(".pdf");
    
    // Shorten title if too long
    const shortName = f.originalFileName || "Berkas Tidak Bernama";
    const dateFormatted = f.createdAt ? new Date(f.createdAt).toLocaleString() : "-";
    const displaySize = formatBytes(f.size || 0);

    let accountInfo = "";
    if (f.provider === "GOOGLE_DRIVE" && f.externalAccountId) {
      const acc = connectedGoogleAccounts.find(a => a.id === f.externalAccountId);
      if (acc) {
        accountInfo = ` (${acc.email})`;
      }
    }

    const providerBadge = f.provider === "GOOGLE_DRIVE" 
      ? `<span class="badge" style="background: rgba(59, 130, 246, 0.15) !important; color: #3b82f6 !important; border: 1px solid rgba(59, 130, 246, 0.3) !important; font-size: 0.7rem; padding: 2px 6px; border-radius: 4px; font-weight: 600; display: inline-flex; align-items: center; gap: 4px; margin-left: 0;">
           <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor"><path d="M19.35 10.04A7.49 7.49 0 0012 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 000 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z"/></svg> Google Drive${accountInfo}
         </span>`
      : `<span class="badge" style="background: rgba(168, 85, 247, 0.15) !important; color: #a855f7 !important; border: 1px solid rgba(168, 85, 247, 0.3) !important; font-size: 0.7rem; padding: 2px 6px; border-radius: 4px; font-weight: 600; display: inline-flex; align-items: center; gap: 4px; margin-left: 0;">
           <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg> Storage Node
         </span>`;

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
          <div style="display: flex; align-items: center; gap: 8px; margin-top: 4px; margin-bottom: 4px;">
            <span class="file-card-size">${displaySize}</span>
            ${providerBadge}
          </div>
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
        ${isPdf ? `
        <button class="btn-ai-action" data-id="${f.id}" title="Rangkum dengan AI">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="15" height="15">
            <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12 6.48 2 12 2z"/>
            <path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
          </svg>
          <span>Rangkum AI</span>
        </button>
        ` : ''}
        ${!currentViewIsShared ? `
        <button class="btn-share-action" data-id="${f.id}" title="Bagikan File">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
          </svg>
        </button>
        <button class="btn-delete-action" data-id="${f.id}" title="Hapus File">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/>
          </svg>
        </button>
        ` : ''}
      </div>
    `;

    // Dynamic action event mapping
    card.querySelector(".btn-download-action").onclick = () => {
      handleDownload(f.id, f.originalFileName, f.size, f.provider);
    };

    if (!currentViewIsShared) {
      card.querySelector(".btn-share-action").onclick = () => {
        handleShare(f.id);
      };

      card.querySelector(".btn-delete-action").onclick = () => {
        handleDelete(f.id, f.provider);
      };
    }

    // Bind AI summarize button if it exists (PDF only)
    const aiBtn = card.querySelector(".btn-ai-action");
    if (aiBtn) {
      aiBtn.onclick = () => handleSummarizePdf(f.id, f.originalFileName);
    }

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

// AI PDF SUMMARIZATION
function showAiSummaryModal(fileName) {
  document.getElementById("aiModalFileName").textContent = `📄 ${fileName}`;
  document.getElementById("aiSummaryLoading").classList.remove("hidden");
  document.getElementById("aiSummaryResult").classList.add("hidden");
  document.getElementById("aiSummaryError").classList.add("hidden");
  document.getElementById("aiSummaryModal").classList.remove("hidden");
}

function closeAiSummaryModal() {
  document.getElementById("aiSummaryModal").classList.add("hidden");
}

async function handleSummarizePdf(fileId, fileName) {
  const activeToken = getToken();
  if (!activeToken) {
    clearSession("Login diperlukan.");
    return;
  }

  showAiSummaryModal(fileName);

  try {
    const response = await debugFetch("summarize-pdf", `/api/ai/summary/pdf/${fileId}`, {
      method: "POST",
      headers: authHeaders()
    });

    if (response.status === 401 || response.status === 403) {
      clearSession("Akses ditolak.");
      closeAiSummaryModal();
      return;
    }

    if (!response.ok) {
      document.getElementById("aiSummaryLoading").classList.add("hidden");
      document.getElementById("aiSummaryError").classList.remove("hidden");
      document.getElementById("aiSummaryErrorText").textContent =
        `Gagal merangkum dokumen (HTTP ${response.status}). Pastikan file adalah PDF yang valid.`;
      return;
    }

    const data = await response.json();
    const summaryText = data.response || "Tidak ada ringkasan yang dihasilkan.";

    document.getElementById("aiSummaryLoading").classList.add("hidden");
    document.getElementById("aiSummaryText").textContent = summaryText;
    document.getElementById("aiSummaryResult").classList.remove("hidden");

  } catch (err) {
    document.getElementById("aiSummaryLoading").classList.add("hidden");
    document.getElementById("aiSummaryError").classList.remove("hidden");
    document.getElementById("aiSummaryErrorText").textContent =
      "Koneksi gagal. Pastikan server berjalan dan coba lagi.";
  }
}

document.getElementById("closeAiModalBtn").addEventListener("click", closeAiSummaryModal);
document.getElementById("aiSummaryModal").addEventListener("click", (e) => {
  if (e.target === document.getElementById("aiSummaryModal")) closeAiSummaryModal();
});
document.getElementById("copyAiResultBtn").addEventListener("click", () => {
  const text = document.getElementById("aiSummaryText").textContent;
  navigator.clipboard.writeText(text).then(() => {
    showToast("Ringkasan berhasil disalin!", "success");
  }).catch(() => {
    showToast("Gagal menyalin teks.", "error");
  });
});


// INITIAL EVENT BINDINGS
document.getElementById("loginForm").addEventListener("submit", handleLogin);
document.getElementById("toRegisterLink").addEventListener("click", (e) => {
  e.preventDefault();
  loginPage.classList.add("hidden");
  registerPage.classList.remove("hidden");
});
document.getElementById("toLoginLink").addEventListener("click", (e) => {
  e.preventDefault();
  registerPage.classList.add("hidden");
  loginPage.classList.remove("hidden");
});
document.getElementById("registerForm").addEventListener("submit", handleRegister);
document.getElementById("otpForm").addEventListener("submit", handleVerifyOtp);
document.getElementById("forgotPasswordForm").addEventListener("submit", handleForgotPassword);

document.getElementById("toForgotPasswordLink").addEventListener("click", (e) => {
  e.preventDefault();
  loginPage.classList.add("hidden");
  forgotPasswordPage.classList.remove("hidden");
});
document.getElementById("otpToLoginLink").addEventListener("click", (e) => {
  e.preventDefault();
  otpPage.classList.add("hidden");
  loginPage.classList.remove("hidden");
});
document.getElementById("fpToLoginLink").addEventListener("click", (e) => {
  e.preventDefault();
  document.getElementById("forgotPasswordForm").reset();
  document.getElementById("fpStep2Fields").classList.add("hidden");
  document.getElementById("fpEmail").removeAttribute("readonly");
  document.getElementById("fpSubmitText").textContent = "Kirim Kode OTP";
  document.getElementById("fpSubHeading").textContent = "Masukkan email Anda untuk menerima kode pemulihan";
  document.getElementById("fpOtpCode").removeAttribute("required");
  document.getElementById("fpNewPassword").removeAttribute("required");
  document.getElementById("fpConfirmNewPassword").removeAttribute("required");
  forgotPasswordPage.classList.add("hidden");
  loginPage.classList.remove("hidden");
});

document.getElementById("logoutButton").addEventListener("click", handleLogout);
document.getElementById("uploadForm").addEventListener("submit", handleUpload);
document.getElementById("storageProvider").addEventListener("change", toggleStorageProviderSelect);
document.getElementById("getAllButton").addEventListener("click", fetchMyFiles);
document.getElementById("getSharedButton").addEventListener("click", () => fetchFiles("/api/files/share/shared-with-me", "get-shared", true));
document.getElementById("getAllAdminButton").addEventListener("click", () => fetchFiles("/api/files/admin", "get-all-admin", false));
document.getElementById("adminPanelButton").addEventListener("click", openAdminDashboard);
document.getElementById("closeAdminPanelBtn").addEventListener("click", closeAdminDashboard);
document.getElementById("closeQuotaModalBtn").addEventListener("click", closeQuotaModal);
document.getElementById("quotaForm").addEventListener("submit", submitQuotaUpdate);
document.getElementById("clearDebugButton").addEventListener("click", () => {
  debugLog.textContent = "";
});

// Preset quota buttons event bindings
document.querySelectorAll(".preset-btn").forEach(btn => {
  btn.addEventListener("click", (e) => {
    const bytes = e.currentTarget.getAttribute("data-bytes");
    document.getElementById("quotaInput").value = bytes;
    updateHumanReadableQuota(bytes);
  });
});

// Custom quota input real-time formatter
document.getElementById("quotaInput").addEventListener("input", (e) => {
  updateHumanReadableQuota(e.target.value);
});

// Run init checks
setPage();

// --- STORAGE QUOTA & ADMIN PANEL FUNCTIONS ---

async function fetchStorageQuota() {
  const activeToken = getToken();
  if (!activeToken) return;

  try {
    const localRes = await debugFetch("storage-quota", "/api/files/me/storage", { headers: authHeaders() });

    if (localRes && localRes.ok) {
      const localData = await localRes.json();
      userUsedBytes = localData.usedBytes;
      userQuotaBytes = localData.quotaBytes;
      const percentage = userQuotaBytes > 0 ? Math.min(100, Math.round((userUsedBytes / userQuotaBytes) * 100)) : 0;

      document.getElementById("quotaText").textContent = `${formatBytes(userUsedBytes)} dari ${formatBytes(userQuotaBytes)}`;
      document.getElementById("quotaPercentage").textContent = `${percentage}%`;
      
      const quotaProgress = document.getElementById("quotaBarProgress");
      quotaProgress.style.width = `${percentage}%`;
      
      if (percentage >= 90) {
        quotaProgress.style.background = "linear-gradient(135deg, #ef4444 0%, #dc2626 100%)";
      } else {
        quotaProgress.style.background = "linear-gradient(135deg, #a855f7 0%, #3b82f6 100%)";
      }
    }
  } catch (err) {
    console.error("Gagal mengambil kuota storage", err);
  }
}

async function probeAdminAccess() {
  const activeToken = getToken();
  if (!activeToken) return;

  try {
    const response = await debugFetch("probe-admin", "/api/files/storage-summary", {
      headers: authHeaders()
    });
    const adminPanelButton = document.getElementById("adminPanelButton");
    if (response.ok) {
      adminPanelButton.classList.remove("hidden");
    } else {
      adminPanelButton.classList.add("hidden");
    }
  } catch (err) {
    console.error("Gagal probe admin", err);
    document.getElementById("adminPanelButton").classList.add("hidden");
  }
}

function openAdminDashboard() {
  document.querySelector(".dashboard-main").classList.add("hidden");
  document.getElementById("adminDashboardMain").classList.remove("hidden");
  loadAdminDashboard();
}

function closeAdminDashboard() {
  document.getElementById("adminDashboardMain").classList.add("hidden");
  document.querySelector(".dashboard-main").classList.remove("hidden");
}

async function loadAdminDashboard() {
  try {
    const response = await debugFetch("load-admin-summary", "/api/files/storage-summary", {
      headers: authHeaders()
    });
    if (!response.ok) {
      showToast("Gagal memuat rekap admin.", "error");
      return;
    }
    const data = await response.json();
    adminUsersList = data;
    
    document.getElementById("adminStatTotalUsers").textContent = data.length;
    
    const tbody = document.getElementById("adminUserTableBody");
    tbody.innerHTML = "";
    
    data.forEach(user => {
      const percentage = user.quotaBytes > 0 ? Math.min(100, Math.round((user.usedBytes / user.quotaBytes) * 100)) : 0;
      const tr = document.createElement("tr");
      tr.style.borderBottom = "1px solid rgba(255,255,255,0.05)";
      
      tr.innerHTML = `
        <td style="padding: 14px 8px; font-weight: 600; color: var(--text-primary);">${user.username}</td>
        <td style="padding: 14px 8px; color: var(--text-secondary);">${user.email}</td>
        <td style="padding: 14px 8px;">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 0.8rem; font-weight: 700; color: var(--accent-color); min-width: 32px;">${percentage}%</span>
            <div class="progress-bar-container quota-bar-bg" style="height: 6px; width: 120px; flex-shrink: 0; margin: 0;">
              <div class="progress-bar-fill quota-bar-fill" style="width: ${percentage}%; height: 100%;"></div>
            </div>
          </div>
        </td>
        <td style="padding: 14px 8px; font-weight: 500; font-size: 0.85rem; color: var(--text-primary);">
          ${formatBytes(user.usedBytes)} / ${formatBytes(user.quotaBytes)}
        </td>
        <td style="padding: 14px 8px; text-align: center;">
          <button class="secondary small btn-manage-quota" data-userid="${user.userId}" data-username="${user.username}" data-email="${user.email}" data-quota="${user.quotaBytes}" style="min-height: 32px; padding: 4px 10px; font-size: 11px;">
            Atur Kuota
          </button>
        </td>
      `;
      tbody.appendChild(tr);
    });
    
    tbody.querySelectorAll(".btn-manage-quota").forEach(btn => {
      btn.addEventListener("click", (e) => {
        const userId = e.currentTarget.getAttribute("data-userid");
        const username = e.currentTarget.getAttribute("data-username");
        const email = e.currentTarget.getAttribute("data-email");
        const currentQuota = e.currentTarget.getAttribute("data-quota");
        openQuotaModal(userId, username, email, currentQuota);
      });
    });
    
  } catch (err) {
    showToast("Koneksi gagal saat mengambil rekap admin.", "error");
  }
}

function openQuotaModal(userId, username, email, currentQuotaBytes) {
  currentTargetUserId = userId;
  document.getElementById("quotaModalUser").textContent = `${username} (${email})`;
  document.getElementById("quotaInput").value = currentQuotaBytes;
  
  updateHumanReadableQuota(currentQuotaBytes);
  document.getElementById("quotaModal").classList.remove("hidden");
}

function closeQuotaModal() {
  document.getElementById("quotaModal").classList.add("hidden");
  currentTargetUserId = null;
}

function updateHumanReadableQuota(bytes) {
  const num = parseInt(bytes);
  const text = isNaN(num) || num <= 0 ? "" : formatBytes(num);
  document.getElementById("quotaHumanReadable").textContent = text ? `Setara dengan: ${text}` : "";
}

async function submitQuotaUpdate(e) {
  e.preventDefault();
  if (!currentTargetUserId) return;
  
  const newQuotaBytes = parseInt(document.getElementById("quotaInput").value);
  if (isNaN(newQuotaBytes) || newQuotaBytes < 1048576) {
    showToast("Kuota minimal adalah 1 MB.", "error");
    return;
  }
  
  try {
    const response = await debugFetch("update-quota", `/api/files/users/${currentTargetUserId}/quota`, {
      method: "PUT",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ quotaBytes: newQuotaBytes })
    });
    
    if (response.ok) {
      showToast("Kuota penyimpanan berhasil diperbarui!", "success");
      closeQuotaModal();
      loadAdminDashboard();
      fetchStorageQuota();
    } else {
      showToast("Gagal memperbarui kuota.", "error");
    }
  } catch (err) {
    showToast("Koneksi terputus saat memperbarui kuota.", "error");
  }
}
