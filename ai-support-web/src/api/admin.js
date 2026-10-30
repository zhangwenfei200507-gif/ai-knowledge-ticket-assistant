export const getOperationsCenter = async () => {
  const token = localStorage.getItem('aegiscloud_token');

  const response = await fetch('/api/admin/dashboard/operations-center', {
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
    },
  });

  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok || body?.code === 0) {
    throw new Error(body?.message || 'Unable to load operations center.');
  }

  return body?.data ?? null;
};

export const getLlmGatewayStatus = () => requestJson('/api/admin/ai/llm/status');

const requestJson = async (path, options = {}) => {
  const token = localStorage.getItem('aegiscloud_token');
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      Authorization: token ? `Bearer ${token}` : '',
      ...(options.headers ?? {}),
    },
  });

  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok || body?.code === 0) {
    throw new Error(body?.message || 'Request failed.');
  }

  return body?.data ?? null;
};

const buildQuery = (params = {}) => {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      searchParams.set(key, value);
    }
  });
  const query = searchParams.toString();
  return query ? `?${query}` : '';
};

export const getKnowledgeArticles = (params = {}) => requestJson(`/api/knowledge/articles${buildQuery(params)}`);

export const getKnowledgeArticle = (id) => requestJson(`/api/knowledge/articles/${id}`);

export const searchKnowledgeArticles = (keyword) => requestJson(`/api/knowledge/articles/search?keyword=${encodeURIComponent(keyword)}`);

export const getKnowledgeDocuments = (params = {}) => requestJson(`/api/documents${buildQuery(params)}`);

export const getKnowledgeDocument = (id) => requestJson(`/api/documents/${id}`);

export const getDocumentChunks = (documentId) => requestJson(`/api/documents/${documentId}/chunks`);

export const getAdminChunks = (params = {}) => requestJson(`/api/admin/chunks${buildQuery(params)}`);

export const updateAdminChunk = (id, payload) => requestJson(`/api/admin/chunks/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload),
});

export const archiveAdminChunk = (id) => requestJson(`/api/admin/chunks/${id}/archive`, {
  method: 'PATCH',
});

export const searchDocumentChunks = (keyword) => requestJson(`/api/documents/chunks/search?keyword=${encodeURIComponent(keyword)}`);

export const getAiQuestionLogs = (params = {}) => requestJson(`/api/ai/logs${buildQuery(params)}`);

export const deleteAiQuestionLog = (id) => requestJson(`/api/ai/logs/${id}`, {
  method: 'DELETE',
});

export const getKnowledgeGaps = (params = {}) => requestJson(`/api/ai/knowledge-gaps${buildQuery(params)}`);

export const createArticleFromKnowledgeGap = (logId, payload) => requestJson(`/api/ai/knowledge-gaps/${logId}/article`, {
  method: 'POST',
  body: JSON.stringify(payload),
});

export const getTickets = (params = {}) => requestJson(`/api/tickets${buildQuery(params)}`);

export const getTicketDetail = (id) => requestJson(`/api/tickets/${id}`);

export const updateTicketStatus = (id, status) => requestJson(`/api/tickets/${id}/status`, {
  method: 'PATCH',
  body: JSON.stringify({ status }),
});

export const resolveTicket = (id) => requestJson(`/api/tickets/${id}/resolve`, {
  method: 'PATCH',
});

export const closeTicket = (id) => requestJson(`/api/tickets/${id}/close`, {
  method: 'PATCH',
});

export const getWebEvidence = (params = {}) => requestJson(`/api/admin/web-evidence${buildQuery(params)}`);

export const getWebEvidenceSummary = () => requestJson('/api/admin/web-evidence/summary');

export const searchWebEvidence = (question) => requestJson('/api/admin/web-evidence/search', {
  method: 'POST',
  body: JSON.stringify({ question }),
});

export const approveWebEvidence = (id) => requestJson(`/api/admin/web-evidence/${id}/approve`, {
  method: 'POST',
});

export const rejectWebEvidence = (id) => requestJson(`/api/admin/web-evidence/${id}/reject`, {
  method: 'PATCH',
});

export const updateWebEvidenceSuggestion = (id, payload) => requestJson(`/api/admin/web-evidence/${id}/suggested-article`, {
  method: 'PUT',
  body: JSON.stringify(payload),
});

export const archiveWebEvidence = (id) => requestJson(`/api/admin/web-evidence/${id}/archive`, {
  method: 'PATCH',
});

export const deleteWebEvidence = (id) => requestJson(`/api/admin/web-evidence/${id}`, {
  method: 'DELETE',
});

export const getDocumentKnowledgeCandidates = (params = {}) => requestJson(`/api/admin/document-knowledge-candidates${buildQuery(params)}`);

export const runKnowledgeSteward = (limit = 20) => requestJson(`/api/admin/knowledge-steward/run${buildQuery({ limit })}`, { method: 'POST' });

export const getKnowledgeStewardTasks = (params = {}) => requestJson(`/api/admin/knowledge-steward/tasks${buildQuery(params)}`);

export const approveKnowledgeStewardTask = (id) => requestJson(`/api/admin/knowledge-steward/tasks/${id}/approve`, { method: 'POST' });

export const rejectKnowledgeStewardTask = (id) => requestJson(`/api/admin/knowledge-steward/tasks/${id}/reject`, { method: 'PATCH' });

export const archiveKnowledgeStewardTask = (id) => requestJson(`/api/admin/knowledge-steward/tasks/${id}/archive`, { method: 'PATCH' });

export const approveDocumentKnowledgeCandidate = (id) => requestJson(`/api/admin/document-knowledge-candidates/${id}/approve`, {
  method: 'POST',
});

export const rejectDocumentKnowledgeCandidate = (id) => requestJson(`/api/admin/document-knowledge-candidates/${id}/reject`, {
  method: 'PATCH',
});

export const updateDocumentKnowledgeCandidate = (id, payload) => requestJson(`/api/admin/document-knowledge-candidates/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload),
});

export const archiveDocumentKnowledgeCandidate = (id) => requestJson(`/api/admin/document-knowledge-candidates/${id}/archive`, {
  method: 'PATCH',
});

export const deleteDocumentKnowledgeCandidate = (id) => requestJson(`/api/admin/document-knowledge-candidates/${id}`, {
  method: 'DELETE',
});

export const deleteKnowledgeGap = (id) => requestJson(`/api/ai/knowledge-gaps/${id}`, {
  method: 'DELETE',
});

export const getCurrentUser = () => requestJson('/api/users/me');

export const getAdminUsers = (params = {}) => requestJson(`/api/admin/users${buildQuery(params)}`);

export const getAdminUser = (id) => requestJson(`/api/admin/users/${id}`);

export const updateAdminUserAccess = (id, payload) => requestJson(`/api/admin/users/${id}/access`, {
  method: 'PATCH',
  body: JSON.stringify(payload),
});

export const logoutCurrentUser = () => requestJson('/api/users/logout', {
  method: 'POST',
});

export const updateKnowledgeArticle = (id, payload) => requestJson(`/api/knowledge/articles/${id}`, {
  method: 'PUT',
  body: JSON.stringify(payload),
});

export const archiveKnowledgeArticle = (id) => requestJson(`/api/knowledge/articles/${id}/archive`, {
  method: 'PATCH',
});

export const archiveKnowledgeDocument = (id) => requestJson(`/api/documents/${id}/archive`, {
  method: 'PATCH',
});

export const rebuildKnowledgeLibrary = () => requestJson('/api/admin/knowledge-maintenance/rebuild', {
  method: 'POST',
});

export const uploadKnowledgeArticle = ({ file, category, title }) => {
  const formData = new FormData();
  formData.append('file', file);
  if (category) {
    formData.append('category', category);
  }
  if (title) {
    formData.append('title', title);
  }

  return requestJson('/api/knowledge/articles/upload', {
    method: 'POST',
    body: formData,
  });
};

export const uploadKnowledgeDocument = ({ file, category }) => {
  const formData = new FormData();
  formData.append('file', file);
  if (category) {
    formData.append('category', category);
  }

  return requestJson('/api/documents/upload', {
    method: 'POST',
    body: formData,
  });
};

export const askAiQuestion = (payload) => {
  if (payload?.file) {
    const formData = new FormData();
    formData.append('question', payload.question);
    formData.append('file', payload.file);
    return requestJson('/api/ai/ask-with-file', {
      method: 'POST',
      body: formData,
    });
  }

  return requestJson('/api/ai/ask', {
    method: 'POST',
    body: JSON.stringify({ question: payload.question }),
  });
};
