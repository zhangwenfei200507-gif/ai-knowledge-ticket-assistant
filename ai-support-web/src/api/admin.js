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

export const getKnowledgeArticles = () => requestJson('/api/knowledge/articles');

export const searchKnowledgeArticles = (keyword) => requestJson(`/api/knowledge/articles/search?keyword=${encodeURIComponent(keyword)}`);

export const getKnowledgeDocuments = () => requestJson('/api/documents');

export const getDocumentChunks = (documentId) => requestJson(`/api/documents/${documentId}/chunks`);

export const searchDocumentChunks = (keyword) => requestJson(`/api/documents/chunks/search?keyword=${encodeURIComponent(keyword)}`);

export const getAiQuestionLogs = () => requestJson('/api/ai/logs');

export const getKnowledgeGaps = () => requestJson('/api/ai/knowledge-gaps');

export const getTickets = () => requestJson('/api/tickets');

export const getWebEvidence = () => requestJson('/api/admin/web-evidence');

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

export const getCurrentUser = () => requestJson('/api/users/me');

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

export const askAiQuestion = (payload) => requestJson('/api/ai/ask', {
  method: 'POST',
  body: JSON.stringify(payload),
});
