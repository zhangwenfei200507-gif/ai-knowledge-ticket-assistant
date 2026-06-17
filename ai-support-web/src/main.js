import {
  ClipboardCheck,
  LayoutDashboard,
  Library,
  LogIn,
  LogOut,
  Search,
  Sparkles,
  TicketCheck,
  TicketPlus,
  createIcons
} from "lucide";
import "./styles.css";

const icons = {
  ClipboardCheck,
  LayoutDashboard,
  Library,
  LogIn,
  LogOut,
  Search,
  Sparkles,
  TicketCheck,
  TicketPlus
};

const state = {
  token: localStorage.getItem("aegis.token") || "",
  user: JSON.parse(localStorage.getItem("aegis.user") || "null"),
  sources: [],
  selectedSourceIndex: -1
};

const elements = {
  loginPanel: document.getElementById("loginPanel"),
  askPanel: document.getElementById("askPanel"),
  loginForm: document.getElementById("loginForm"),
  askForm: document.getElementById("askForm"),
  usernameInput: document.getElementById("usernameInput"),
  passwordInput: document.getElementById("passwordInput"),
  questionInput: document.getElementById("questionInput"),
  sampleButton: document.getElementById("sampleButton"),
  createTicketButton: document.getElementById("createTicketButton"),
  logoutButton: document.getElementById("logoutButton"),
  userLabel: document.getElementById("userLabel"),
  answerPanel: document.getElementById("answerPanel"),
  answerSourceTitle: document.getElementById("answerSourceTitle"),
  answerText: document.getElementById("answerText"),
  sourceBadge: document.getElementById("sourceBadge"),
  sourceCount: document.getElementById("sourceCount"),
  sourceList: document.getElementById("sourceList"),
  detailTitle: document.getElementById("detailTitle"),
  detailMeta: document.getElementById("detailMeta"),
  detailPreview: document.getElementById("detailPreview"),
  ticketStrip: document.getElementById("ticketStrip"),
  ticketMessage: document.getElementById("ticketMessage"),
  toast: document.getElementById("toast")
};

createIcons({ icons });

function updateSessionView() {
  const signedIn = Boolean(state.token);
  elements.loginPanel.hidden = signedIn;
  elements.askPanel.hidden = !signedIn;
  elements.logoutButton.hidden = !signedIn;

  if (signedIn && state.user) {
    const displayName = state.user.displayName || state.user.username;
    elements.userLabel.textContent = `${displayName} / ${state.user.role}`;
  } else {
    elements.userLabel.textContent = "Not signed in";
  }
}

async function request(path, options = {}) {
  const headers = {
    ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
    ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
    ...(options.headers || {})
  };

  const response = await fetch(path, { ...options, headers });
  const payload = await response.json().catch(() => null);

  if (!response.ok || !payload || payload.code !== 1) {
    throw new Error((payload && payload.message) || `Request failed with ${response.status}`);
  }

  return payload.data;
}

function persistSession(data) {
  state.token = data.token;
  state.user = data.user;
  localStorage.setItem("aegis.token", state.token);
  localStorage.setItem("aegis.user", JSON.stringify(state.user));
  updateSessionView();
}

function clearSession() {
  state.token = "";
  state.user = null;
  state.sources = [];
  state.selectedSourceIndex = -1;
  localStorage.removeItem("aegis.token");
  localStorage.removeItem("aegis.user");
  updateSessionView();
  renderSources([]);
  elements.answerPanel.hidden = true;
}

function showToast(message, type = "info") {
  elements.toast.textContent = message;
  elements.toast.className = type === "error" ? "toast error" : "toast";
  elements.toast.hidden = false;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => {
    elements.toast.hidden = true;
  }, 3200);
}

function sourceLabel(source) {
  if (source.sourceType === "DOCUMENT") {
    return source.sourceDocumentName || "Document source";
  }
  if (source.sourceType === "ARTICLE") {
    return source.sourceTitle || "Knowledge article";
  }
  return "No internal source";
}

function sourceMeta(source) {
  if (source.sourceType === "DOCUMENT") {
    const score = typeof source.sourceScore === "number" ? ` / score ${source.sourceScore.toFixed(3)}` : "";
    return `Document chunk ${source.sourceChunkIndex ?? "-"}${score}`;
  }
  if (source.sourceType === "ARTICLE") {
    return `Article ${source.sourceArticleId ?? "-"}`;
  }
  return "Fallback";
}

function shortText(value, limit = 150) {
  if (!value) {
    return "";
  }
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length > limit ? `${normalized.slice(0, limit - 3)}...` : normalized;
}

function renderSources(sources) {
  state.sources = sources || [];
  elements.sourceCount.textContent = String(state.sources.length);
  elements.sourceList.innerHTML = "";

  if (state.sources.length === 0) {
    elements.sourceList.innerHTML = '<div class="empty-state">No sources selected.</div>';
    renderSourceDetail(null);
    return;
  }

  state.sources.forEach((source, index) => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = index === state.selectedSourceIndex ? "source-card active" : "source-card";

    const title = document.createElement("div");
    title.className = "source-card-title";
    const titleText = document.createElement("span");
    titleText.textContent = sourceLabel(source);
    title.appendChild(titleText);

    const meta = document.createElement("div");
    meta.className = "source-card-meta";
    meta.textContent = sourceMeta(source);

    const preview = document.createElement("p");
    preview.className = "source-card-preview";
    preview.textContent = shortText(source.previewContent);

    card.append(title, meta, preview);
    card.addEventListener("click", () => selectSource(index));
    elements.sourceList.appendChild(card);
  });

  if (state.selectedSourceIndex < 0) {
    selectSource(0);
  }
}

function selectSource(index) {
  state.selectedSourceIndex = index;
  renderSourceDetail(state.sources[index]);
  [...elements.sourceList.querySelectorAll(".source-card")].forEach((card, cardIndex) => {
    card.classList.toggle("active", cardIndex === index);
  });
}

function renderSourceDetail(source) {
  if (!source) {
    elements.detailTitle.textContent = "Nothing selected";
    elements.detailMeta.textContent = "Run a question to inspect evidence.";
    elements.detailPreview.textContent = "";
    return;
  }

  elements.detailTitle.textContent = sourceLabel(source);
  elements.detailMeta.textContent = sourceMeta(source);
  elements.detailPreview.textContent = source.previewContent || "No preview content returned.";
}

function renderAnswer(answer) {
  elements.answerPanel.hidden = false;
  elements.answerText.textContent = answer.answer || "";
  elements.answerSourceTitle.textContent = answer.sourceType === "NONE" ? "Needs human follow-up" : "Internal source matched";
  elements.sourceBadge.textContent = answer.sourceType || "NONE";
  elements.sourceBadge.className = "source-badge";

  if (answer.sourceType === "ARTICLE") {
    elements.sourceBadge.classList.add("article");
  }
  if (answer.sourceType === "NONE") {
    elements.sourceBadge.classList.add("none");
  }

  if (answer.createdTicketId) {
    elements.ticketStrip.hidden = false;
    elements.ticketMessage.textContent = `Ticket ${answer.createdTicketId} was created.`;
  } else if (answer.suggestCreateTicket) {
    elements.ticketStrip.hidden = false;
    elements.ticketMessage.textContent = "No reliable internal answer was found.";
  } else {
    elements.ticketStrip.hidden = true;
  }

  state.selectedSourceIndex = -1;
  renderSources(answer.sources || []);
}

async function handleLogin(event) {
  event.preventDefault();
  const username = elements.usernameInput.value.trim();
  const password = elements.passwordInput.value;

  try {
    const data = await request("/api/users/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    persistSession(data);
    showToast("Signed in.");
  } catch (error) {
    showToast(error.message, "error");
  }
}

async function askQuestion(createTicketIfNeeded = false) {
  const question = elements.questionInput.value.trim();
  if (!question) {
    showToast("Enter a question first.", "error");
    return;
  }

  const endpoint = createTicketIfNeeded ? "/api/ai/ask-and-create-ticket" : "/api/ai/ask";
  const originalButtonText = createTicketIfNeeded ? elements.createTicketButton.textContent : null;

  try {
    if (createTicketIfNeeded) {
      elements.createTicketButton.textContent = "Working...";
      elements.createTicketButton.disabled = true;
    }
    const answer = await request(endpoint, {
      method: "POST",
      body: JSON.stringify({ question })
    });
    renderAnswer(answer);
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    if (createTicketIfNeeded) {
      elements.createTicketButton.textContent = originalButtonText;
      elements.createTicketButton.disabled = false;
      createIcons({ icons });
    }
  }
}

elements.loginForm.addEventListener("submit", handleLogin);

elements.askForm.addEventListener("submit", (event) => {
  event.preventDefault();
  askQuestion(false);
});

elements.createTicketButton.addEventListener("click", () => askQuestion(true));

elements.sampleButton.addEventListener("click", () => {
  elements.questionInput.value = "How should I handle a production cache server outage?";
  elements.questionInput.focus();
});

elements.logoutButton.addEventListener("click", async () => {
  try {
    if (state.token) {
      await request("/api/users/logout", { method: "POST" });
    }
  } catch (error) {
    showToast(error.message, "error");
  } finally {
    clearSession();
  }
});

updateSessionView();
