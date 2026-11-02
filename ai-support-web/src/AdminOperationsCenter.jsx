import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Bell,
  BookOpen,
  Bot,
  CalendarDays,
  ChevronDown,
  CircleHelp,
  Cloud,
  Code2,
  Database,
  Eye,
  FileText,
  Gauge,
  GitBranch,
  Globe2,
  Home,
  Menu,
  Megaphone,
  Maximize2,
  Minimize2,
  Network,
  PanelLeftClose,
  RefreshCw,
  Search,
  Server,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Target,
  Ticket,
  Upload,
  UserRound,
  UsersRound,
} from 'lucide-react';
import AdminUserManagement from './components/AdminUserManagement';
import {
  approveDocumentKnowledgeCandidate,
  approveKnowledgeStewardTask,
  approveWebEvidence,
  archiveAdminChunk,
  archiveDocumentKnowledgeCandidate,
  archiveKnowledgeStewardTask,
  archiveKnowledgeArticle,
  archiveKnowledgeDocument,
  archiveWebEvidence,
  askAiQuestion,
  closeTicket,
  createArticleFromKnowledgeGap,
  deleteDocumentKnowledgeCandidate,
  deleteAiQuestionLog,
  deleteKnowledgeGap,
  deleteWebEvidence,
  getAiQuestionLogs,
  getAdminChunks,
  getCurrentUser,
  getKnowledgeArticle,
  getDocumentKnowledgeCandidates,
  getDocumentChunks,
  getKnowledgeDocument,
  getKnowledgeGaps,
  getKnowledgeStewardTasks,
  getKnowledgeArticles,
  getKnowledgeDocuments,
  getLlmGatewayStatus,
  getOperationsCenter,
  getTickets,
  getTicketDetail,
  getWebEvidence,
  getWebEvidenceSummary,
  logoutCurrentUser,
  rebuildKnowledgeLibrary,
  rejectDocumentKnowledgeCandidate,
  rejectKnowledgeStewardTask,
  rejectWebEvidence,
  resolveTicket,
  runKnowledgeSteward,
  searchDocumentChunks,
  searchKnowledgeArticles,
  searchWebEvidence,
  updateDocumentKnowledgeCandidate,
  updateAdminChunk,
  updateKnowledgeArticle,
  updateTicketStatus,
  updateWebEvidenceSuggestion,
  uploadKnowledgeArticle,
  uploadKnowledgeDocument,
} from './api/admin';

const fallbackData = {
  environment: { name: 'Unknown', status: 'UNKNOWN' },
  serviceHealth: [
    { name: 'API', status: 'UNKNOWN' },
    { name: 'Search', status: 'UNKNOWN' },
    { name: 'LLM Gateway', status: 'NOT_CONFIGURED' },
    { name: 'Storage', status: 'UNKNOWN' },
  ],
  metrics: [
    { key: 'totalKnowledgeArticles', label: 'Knowledge Packs', value: '—', trend: 'Unavailable', trendType: 'neutral', detail: 'Waiting for live backend data', icon: 'article' },
    { key: 'runbookDocuments', label: 'Source Documents', value: '—', trend: 'Unavailable', trendType: 'neutral', detail: 'Waiting for live backend data', icon: 'book' },
    { key: 'knowledgeCoverage', label: 'Knowledge Coverage', value: '—', trend: 'Unavailable', trendType: 'warning', detail: 'Requires live article and domain data', icon: 'coverage' },
    { key: 'questionsAnswered7d', label: 'Questions Answered (7D)', value: '—', trend: 'Unavailable', trendType: 'neutral', detail: 'Waiting for live AI activity data', icon: 'question' },
    { key: 'avgAnswerConfidence', label: 'Avg Answer Confidence', value: '—', trend: 'Unavailable', trendType: 'neutral', detail: 'Waiting for live AI activity data', icon: 'target' },
    { key: 'openTickets', label: 'Open Tickets', value: '—', trend: 'Unavailable', trendType: 'neutral', detail: 'Waiting for live ticket data', icon: 'ticket' },
  ],
  knowledgeCore: { categories: [], trustedSources: null },
  ingestionPipeline: { steps: [], recentJobs: [] },
  externalEvidence: {
    queue: [],
    summary: { searches: 0, usefulEvidence: 0, convertedToArticles: 0, pendingReview: 0 },
  },
  reasoningLayer: {
    intentDistribution: [],
    totalQuestions: 0,
    avgConfidence: 0,
    citationCoverage: 0,
    hallucinationRisk: 0,
    sourceMix: [],
  },
  knowledgeGapLoop: { gaps: [] },
  ticketEscalation: { tickets: [] },
  answerAnalytics: {
    usefulnessPercent: 0,
    usefulCount: 0,
    partiallyUsefulCount: 0,
    notUsefulCount: 0,
    coverageTrend: 0,
    topCategoriesByQuestions: [],
  },
  modelSearchControls: {
    modelProfile: {
      primaryModel: 'Not reported',
      embeddingModel: 'Not reported',
      rerankerModel: 'Not reported',
      status: 'UNKNOWN',
    },
    retrievalStrategy: {
      topKDocuments: null,
      topKArticles: null,
      topKWebEvidence: null,
      rerankerEnabled: null,
    },
    webSearchSettings: {
      webFallbackThreshold: null,
      trustedDomains: null,
      maxWebResults: null,
    },
  },
  adminTip: 'Review high priority knowledge gaps and convert valuable web evidence into internal articles to improve answer quality.',
};

const metricIconMap = {
  article: FileText,
  book: BookOpen,
  coverage: Gauge,
  question: CircleHelp,
  target: Target,
  ticket: Ticket,
};

const categoryIconMap = {
  'Auth / JWT': ShieldCheck,
  'Redis Cache': Server,
  PostgreSQL: Database,
  Docker: Cloud,
  Kubernetes: Network,
  'Incident SOP': AlertTriangle,
  'Ticket Rules': Ticket,
  'Customer Templates': FileText,
};

const pageHeadings = {
  command: ['Dashboard', 'System overview and key operational metrics'],
  assistant: ['AI Assistant', 'Search your knowledge, investigate issues, and get grounded answers'],
  knowledge: ['Knowledge Library', 'Maintain reviewed, durable knowledge packs'],
  knowledgePackDetail: ['Knowledge Pack', 'Read and maintain one published knowledge pack'],
  articleDetail: ['Article', 'Read and maintain one published knowledge article'],
  documents: ['Source Library', 'Manage uploaded documents and retrieval sources'],
  documentDetail: ['Document Details', 'Inspect one uploaded source document and its retrieval units'],
  documentLearning: ['Knowledge Review', 'Review and approve knowledge candidates'],
  webEvidence: ['Web Evidence', 'Review external evidence before it becomes internal knowledge'],
  ai: ['AI Activity', 'Inspect real AI usage, grounding, and answer quality'],
  gaps: ['Knowledge Gaps', 'Find and resolve unanswered or low-confidence questions'],
  tickets: ['Tickets', 'Manage support requests and escalations'],
  users: ['User Management', 'Manage registered users, roles, and account access'],
  settings: ['Settings', 'Inspect system configuration and service readiness'],
};

const LIBRARY_PAGE_SIZE = 8;
const REVIEW_PAGE_SIZE = 6;
const normalizeAdminPath = (path = window.location.pathname) => {
  const normalized = `/${String(path).replace(/^\/+|\/+$/g, '')}`;
  return normalized === '/' ? '/admin' : normalized;
};

const parseAdminRoute = (path = window.location.pathname) => {
  const normalized = normalizeAdminPath(path);
  const packMatch = normalized.match(/^\/admin\/knowledge\/packs\/(\d+)(?:\/edit)?$/);
  const articleMatch = normalized.match(/^\/admin\/knowledge\/articles\/(\d+)(?:\/edit)?$/);
  const documentChunksMatch = normalized.match(/^\/admin\/sources\/documents\/(\d+)\/chunks$/);
  const documentMatch = normalized.match(/^\/admin\/sources\/documents\/(\d+)$/);
  if (packMatch) return { page: 'knowledgePackDetail', id: Number(packMatch[1]), mode: normalized.endsWith('/edit') ? 'edit' : 'view' };
  if (articleMatch) return { page: 'articleDetail', id: Number(articleMatch[1]), mode: normalized.endsWith('/edit') ? 'edit' : 'view' };
  if (documentChunksMatch) return { page: 'documentDetail', id: Number(documentChunksMatch[1]), tab: 'chunks' };
  if (documentMatch) return { page: 'documentDetail', id: Number(documentMatch[1]), tab: 'overview' };

  const pages = [
    ['/admin', 'command'],
    ['/admin/assistant', 'assistant'],
    ['/admin/knowledge', 'knowledge'],
    ['/admin/sources', 'documents'],
    ['/admin/review', 'documentLearning'],
    ['/admin/web-evidence', 'webEvidence'],
    ['/admin/ai-activity', 'ai'],
    ['/admin/gaps', 'gaps'],
    ['/admin/tickets', 'tickets'],
    ['/admin/users', 'users'],
    ['/admin/settings', 'settings'],
  ];
  return {
    page: pages.find(([prefix]) => normalized === prefix
      || (prefix !== '/admin' && normalized.startsWith(`${prefix}/`)))?.[1] || 'command',
  };
};

const pageItems = (pageResult) => pageResult?.items ?? [];
const pageInfo = (pageResult, fallbackSize) => ({
  page: pageResult?.page ?? 1,
  size: pageResult?.size ?? fallbackSize,
  total: pageResult?.total ?? 0,
  pageCount: pageResult?.pageCount ?? 1,
});

function statusText(status) {
  if (!status) return 'Unknown';
  const normalized = String(status).toUpperCase();
  if (normalized === 'WEB_SEARCH') return 'Web Search';
  if (normalized === 'LLM_GENERATED') return 'Model Fallback';
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function answerRouteLabel(answer) {
  const mode = String(answer?.answerMode || answer?.sourceType || '').toUpperCase();
  return {
    INTERNAL_RAG: 'Internal Knowledge',
    WEB_SEARCH: 'Web Search',
    AI_ASSISTED_WEB: 'AI-assisted Web',
    AI_ONLY: 'AI Only',
    NONE: 'No reliable answer',
  }[mode] || (answer?.sourceType ? statusText(answer.sourceType) : 'Unknown route');
}

function answerEngineLabel(answer) {
  if ([answer?.answerMode, answer?.sourceType].some((value) => String(value || '').toUpperCase() === 'NONE')) return 'Insufficient evidence';
  if (String(answer?.answerEngine || '').toUpperCase() === 'LLM') return 'Model synthesized';
  return 'Grounded answer';
}

function hasUsableAnswer(answer) {
  const unavailable = [answer?.answerMode, answer?.sourceType].some((value) => String(value || '').toUpperCase() === 'NONE');
  return Boolean(answer?.answer && String(answer.answer).trim() && !unavailable);
}

function statusClass(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'healthy' || normalized === 'active') return 'healthy';
  if (normalized === 'empty' || normalized === 'not_configured' || normalized === 'configured') return 'neutral';
  return 'warning';
}

function numberValue(value) {
  if (value === null || value === undefined || value === '') return 0;
  return Number(value);
}

function formatCount(value) {
  return numberValue(value).toLocaleString('en-US');
}

function MiniButton({ children, onClick }) {
  return <button className="adminMiniButton" type="button" onClick={onClick}>{children}</button>;
}

function Section({ number, title, action, onAction, children, className = '', id }) {
  return (
    <section className={`adminSection ${className}`} id={id}>
      <div className="adminSectionHeader">
        <h2><span>{number}.</span> {title}</h2>
        {action && <MiniButton onClick={onAction}>{action}</MiniButton>}
      </div>
      {children}
    </section>
  );
}

function EmptyState({ text }) {
  return <div className="adminEmptyState">{text}</div>;
}

function LibraryPager({ page, pageCount, total, onPageChange }) {
  if (total <= LIBRARY_PAGE_SIZE) return null;
  const pages = Array.from({ length: Math.min(pageCount, 5) }, (_, index) => {
    const start = Math.min(Math.max(1, page - 2), Math.max(1, pageCount - 4));
    return start + index;
  });
  return (
    <div className="libraryPager">
      <button type="button" disabled={page <= 1} onClick={() => onPageChange(Math.max(1, page - 1))}>Prev</button>
      {pages.map((number) => <button key={number} type="button" className={number === page ? 'active' : ''} aria-current={number === page ? 'page' : undefined} onClick={() => onPageChange(number)}>{number}</button>)}
      <span>{(page - 1) * LIBRARY_PAGE_SIZE + 1}–{Math.min(page * LIBRARY_PAGE_SIZE, total)} of {total}</span>
      <button type="button" disabled={page >= pageCount} onClick={() => onPageChange(Math.min(pageCount, page + 1))}>Next</button>
    </div>
  );
}

function ReviewPager({ page, pageCount, total, onPageChange, showSinglePage = false }) {
  if (total <= 0 || (total <= REVIEW_PAGE_SIZE && !showSinglePage)) return null;
  const safePage = Math.max(1, page || 1);
  const safePageCount = Math.max(1, pageCount || 1);
  const firstItem = (safePage - 1) * REVIEW_PAGE_SIZE + 1;
  const lastItem = Math.min(safePage * REVIEW_PAGE_SIZE, total);
  const start = Math.min(Math.max(1, safePage - 2), Math.max(1, safePageCount - 4));
  const pages = Array.from({ length: Math.min(safePageCount, 5) }, (_, index) => start + index);
  return (
    <div className="libraryPager reviewPager">
      <button type="button" disabled={safePage <= 1} onClick={() => onPageChange(Math.max(1, safePage - 1))}>Previous</button>
      {pages.map((number) => <button key={number} type="button" className={number === safePage ? 'active' : ''} aria-current={number === safePage ? 'page' : undefined} onClick={() => onPageChange(number)}>{number}</button>)}
      <span>Showing {firstItem}–{lastItem} of {total}</span>
      <button type="button" disabled={safePage >= safePageCount} onClick={() => onPageChange(Math.min(safePageCount, safePage + 1))}>Next</button>
    </div>
  );
}

function MetricCard({ metric, onClick }) {
  const Icon = metricIconMap[metric.icon] || Gauge;

  return (
    <button className={`adminMetricCard ${metric.trendType || 'neutral'}`} type="button" onClick={onClick}>
      <div className="adminMetricIcon"><Icon size={24} /></div>
      <div>
        <p>{metric.label}</p>
        <strong>{metric.value}</strong>
        <span>{metric.trend}</span>
        <small>{metric.detail}</small>
      </div>
    </button>
  );
}

function KnowledgeCore({ data }) {
  const categories = data?.categories ?? [];
  const trusted = data?.trustedSources ?? { domainCount: 0, coveragePercent: 0 };

  return (
    <>
      <div className="knowledgeCoreGrid">
        {categories.map((item) => {
          const Icon = categoryIconMap[item.category] || FileText;
          return (
            <article className="knowledgeCategoryCard" key={item.category}>
              <Icon size={22} />
              <strong>{item.category}</strong>
              <small>{formatCount(item.articleCount)} Articles</small>
              <div className="dualProgress">
                <span>{item.coveragePercent}%</span>
                <div><i style={{ width: `${item.coveragePercent}%` }} /></div>
                <span>{item.freshnessPercent}%</span>
              </div>
            </article>
          );
        })}
      </div>
      <div className="trustedSourcesStrip">
        <Globe2 size={28} />
        <strong>Trusted Sources</strong>
        <span>{formatCount(trusted.domainCount)} Domains</span>
        <div><i style={{ width: `${trusted.coveragePercent}%` }} /></div>
        <b>{trusted.coveragePercent}%</b>
      </div>
    </>
  );
}

function IngestionPipeline({ data }) {
  const jobs = data?.recentJobs ?? [];

  return (
    <>
      <div className="pipelineSteps">
        {(data?.steps ?? []).map((step) => (
          <div className={`pipelineStep ${step.status === 'COMPLETED' ? 'done' : 'waiting'}`} key={step.name}>
            <span>{step.status === 'COMPLETED' ? 'OK' : '...'}</span>
            <p>{step.name}</p>
          </div>
        ))}
      </div>
      <Table
        columns={['Document', 'Type', 'Status', 'Chunks', 'Embeddings', 'Time']}
        empty="No uploaded documents yet."
        rows={jobs.map((job) => [
          job.document,
          job.type,
          <span className={`statusPill ${job.status?.toLowerCase()}`}>{job.status}</span>,
          job.chunks ?? '-',
          job.embeddings ?? '-',
          job.timeAgo,
        ])}
      />
    </>
  );
}

function ExternalEvidence({ data, onOpen, onView }) {
  const queue = data?.queue ?? [];
  const summary = data?.summary ?? {};

  return (
    <div className="webEvidenceSummaryPanel">
      <Table
        columns={['Question', 'Domain', 'Credibility', 'Status', 'Action']}
        empty="No external evidence waiting for review."
        rows={queue.map((item) => [
          item.queryTopic,
          item.topDomain,
          item.credibility,
          <span className="reviewPill">{statusText(item.status)}</span>,
          <button className="iconOnlyButton" type="button" onClick={() => onView?.(item.evidenceId)}><Eye size={14} /></button>,
        ])}
      />
      <div className="summaryBox">
        <strong>Web Evidence Summary <span>(All time)</span></strong>
        <div>
          <p><b>{summary.searches ?? 0}</b><span>Searches</span></p>
          <p><b>{summary.usefulEvidence ?? 0}</b><span>Useful Evidence</span></p>
          <p><b>{summary.convertedToArticles ?? 0}</b><span>Converted to Articles</span></p>
          <p><b className="dangerText">{summary.pendingReview ?? 0}</b><span>Pending Review</span></p>
        </div>
        <button type="button" onClick={onOpen}>Open Web Evidence</button>
      </div>
    </div>
  );
}

function WebEvidenceWorkspace({
  evidence,
  summary,
  selectedEvidence,
  searchValue,
  actionStatus,
  onSearchValueChange,
  onSearch,
  onRefresh,
  onSelect,
  onEdit,
  onApprove,
  onReject,
  onArchive,
  onDelete,
  pageMeta,
  statusFilter,
  originFilter,
  filterKeyword,
  categoryFilter,
  onFilterKeywordChange,
  onSearchFilter,
  onCategoryFilterChange,
  onStatusFilterChange,
  onOriginFilterChange,
  onPageChange,
}) {
  return (
    <section className="webEvidenceWorkspace">
      <div className="webEvidenceHero">
        <div>
          <span>External Retrieval Loop</span>
          <h2>Turn external research into reviewed knowledge.</h2>
          <p>Review verified web sources and clearly separated AI-only candidates before they enter the knowledge base.</p>
        </div>
        <form className="webEvidenceSearch" onSubmit={onSearch}>
          <input value={searchValue} placeholder="Search external evidence for a CloudOps question..." onChange={(event) => onSearchValueChange(event.target.value)} />
          <button type="submit"><Search size={16} />Search Web</button>
        </form>
      </div>

      <div className="webEvidenceStats">
        <article><strong>{summary ? formatCount(summary.total) : '—'}</strong><span>Total Records</span></article>
        <article><strong>{summary ? formatCount(summary.webSources) : '—'}</strong><span>Web Sources</span></article>
        <article><strong>{summary ? formatCount(summary.modelGenerated) : '—'}</strong><span>AI-Generated</span></article>
        <article><strong>{summary ? formatCount(summary.needsReview) : '—'}</strong><span>Needs Review</span></article>
      </div>

      {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}

      <div className="webEvidenceReviewGrid">
        <div className="webEvidenceQueue">
          <div className="libraryToolbar">
            <strong>{pageMeta.total} Evidence Records</strong>
            <input aria-label="Filter evidence records" value={filterKeyword} placeholder="Filter evidence..." onChange={(event) => onFilterKeywordChange(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') onSearchFilter(); }} />
            <select aria-label="Filter evidence by category" value={categoryFilter} onChange={(event) => onCategoryFilterChange(event.target.value)}><option value="">All Categories</option>{[...Object.keys(categoryIconMap), 'General'].map((category) => <option key={category} value={category}>{category}</option>)}</select>
            <select value={statusFilter} onChange={(event) => onStatusFilterChange(event.target.value)}>
              <option value="">All Status</option>
              <option value="NEEDS_REVIEW">Needs Review</option>
              <option value="CONVERTED">Converted</option>
              <option value="REJECTED">Rejected</option>
            </select>
            <select aria-label="Filter by evidence origin" value={originFilter} onChange={(event) => onOriginFilterChange(event.target.value)}>
              <option value="">All Sources</option>
              <option value="WEB_SEARCH">Web Search</option>
              <option value="LLM_GENERATED">AI-Generated</option>
            </select>
            <button type="button" onClick={onRefresh}>Refresh</button>
          </div>
          {evidence.length ? evidence.map((item) => (
            <button className={selectedEvidence?.id === item.id ? 'active' : ''} type="button" key={item.id} onClick={() => onSelect(item)}>
              <strong>{item.evidenceOrigin === 'LLM_GENERATED' ? `AI-generated answer: ${item.question}` : item.title || item.question}</strong>
              <span>{item.evidenceOrigin === 'LLM_GENERATED' ? 'AI fallback · No web source' : item.domain || 'Unknown domain'} · {statusText(item.status)} · {item.category || 'General'}</span>
              <p>{item.question}</p>
            </button>
          )) : <EmptyState text="No external evidence yet. Ask an unknown question or run a manual web search." />}
          <ReviewPager page={pageMeta.page} pageCount={pageMeta.pageCount} total={pageMeta.total} onPageChange={onPageChange} />
        </div>

        <article className="webEvidenceDetail">
          {selectedEvidence ? (
            <>
              <div className="knowledgeReaderHeader">
                <span>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'AI-GENERATED CANDIDATE · NOT WEB EVIDENCE' : 'WEB EVIDENCE'}</span>
                <strong>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? selectedEvidence.suggestedArticleTitle || selectedEvidence.question : selectedEvidence.title || selectedEvidence.question}</strong>
                <small>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'OpenAI fallback · No external source' : selectedEvidence.domain || 'Unknown domain'} · {statusText(selectedEvidence.status)} · {formatDateTime(selectedEvidence.createdTime)}</small>
                <div className="readerActions">
                  {selectedEvidence.url && <button type="button" onClick={() => window.open(selectedEvidence.url, '_blank', 'noopener,noreferrer')}>Open Source</button>}
                  <button type="button" onClick={() => navigator.clipboard.writeText(selectedEvidence.suggestedArticleContent || selectedEvidence.extractedContent || '')}>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'Copy Draft' : 'Copy Evidence'}</button>
                  <button type="button" disabled={selectedEvidence.status !== 'NEEDS_REVIEW'} onClick={() => onEdit(selectedEvidence)}>Edit Before Approve</button>
                  <button type="button" disabled={selectedEvidence.status !== 'NEEDS_REVIEW'} onClick={() => onApprove(selectedEvidence)}>Approve to Article</button>
                  <button className="danger" type="button" disabled={selectedEvidence.status !== 'NEEDS_REVIEW'} onClick={() => onReject(selectedEvidence)}>Reject</button>
                  <button className="danger" type="button" disabled={selectedEvidence.status === 'CONVERTED'} onClick={() => onArchive(selectedEvidence)}>Archive</button>
                  <button className="danger" type="button" disabled={!['REJECTED', 'ARCHIVED'].includes(selectedEvidence.status)} onClick={() => onDelete(selectedEvidence)}>Delete</button>
                </div>
              </div>

              <div className="webEvidenceMeta">
                <InfoGroup title="Source" rows={[
                  ['Evidence Origin', selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'AI-generated fallback (unverified)' : statusText(selectedEvidence.evidenceOrigin || 'WEB_SEARCH')],
                  ['Question', selectedEvidence.question],
                  ['Query', selectedEvidence.query],
                  ['Domain', selectedEvidence.domain],
                  ['Credibility', selectedEvidence.credibilityScore],
                ]} />
                <InfoGroup title="Review" rows={[
                  ['Status', statusText(selectedEvidence.status)],
                  ['Category', selectedEvidence.category],
                  ['Converted Article', selectedEvidence.convertedArticleId],
                  ['Reviewed', formatDateTime(selectedEvidence.reviewedTime)],
                ]} />
              </div>

              <div className="webEvidenceArticlePreview">
                <h3>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'AI-Generated Draft · Needs Verification' : 'Suggested Internal Article'}</h3>
                <KnowledgeContentBlock content={selectedEvidence.suggestedArticleContent || selectedEvidence.extractedContent} />
              </div>
              <div className="webEvidenceArticlePreview">
                <h3>{selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'External Sources' : 'All Retrieved Sources'}</h3>
                {selectedEvidence.sources?.length ? selectedEvidence.sources.map((source) => (
                  <div className="evidenceSourceRow" key={source.id || source.url}>
                    <div>
                      <strong>{source.title || source.domain || 'Untitled source'}</strong>
                      <span>{source.domain || 'Unknown domain'} · {source.sourceType || 'SOURCE'} · {source.officialSource ? 'Official' : 'Technical source'}</span>
                    </div>
                    <small>{source.publishedTime ? `Published ${formatDateTime(source.publishedTime)}` : 'Published date unknown'} · Retrieved {formatDateTime(source.retrievedTime)}</small>
                    {source.url && <a href={source.url} target="_blank" rel="noreferrer">Open source</a>}
                  </div>
                )) : <EmptyState text={selectedEvidence.evidenceOrigin === 'LLM_GENERATED' ? 'No external sources were used. This answer came from model fallback and must be independently verified.' : 'No source-level records were saved for this evidence.'} />}
              </div>
            </>
          ) : <EmptyState text="Select a web evidence record to review its source and suggested article." />}
        </article>
      </div>
    </section>
  );
}

function ReasoningLayer({ data }) {
  const intents = data?.intentDistribution ?? [];
  const sourceMix = data?.sourceMix ?? [];

  return (
    <div className="reasoningGrid">
      <div>
        <h3>Intent Detection</h3>
        {intents.length ? intents.map((intent) => (
          <div className="intentRow" key={intent.intent}>
            <span>{intent.intent}</span>
            <b>{intent.percent}%</b>
          </div>
        )) : <EmptyState text="No AI questions yet." />}
      </div>
      <div className="donutPanel">
        <div className="donutRing" style={{ '--value': `${Math.max(numberValue(data?.citationCoverage), 8)}%` }}>
          <b>{formatCount(data?.totalQuestions)}</b>
          <span>Total Questions</span>
        </div>
      </div>
      <div>
        <h3>Answer Quality</h3>
        <MetricLine label="Avg Confidence" value={data?.avgConfidence ?? 0} />
        <MetricLine label="Citation Coverage" value={`${data?.citationCoverage ?? 0}%`} />
        <MetricLine label="Hallucination Risk" value={`${data?.hallucinationRisk ?? 0}%`} danger />
      </div>
      <div className="sourceMix">
        {sourceMix.map((source) => (
          <div key={source.source} style={{ width: `${Math.max(source.percent, 4)}%` }} title={source.source} />
        ))}
      </div>
    </div>
  );
}

function MetricLine({ label, value, danger }) {
  return (
    <div className="metricLine">
      <span>{label}</span>
      <b className={danger ? 'dangerText' : ''}>{value}</b>
    </div>
  );
}

function KnowledgeGaps({ data, onView, onDelete, keyword, onKeywordChange, onSearch, category, onCategoryChange, status, onStatusChange, pageMeta, onPageChange }) {
  const gaps = data?.gaps ?? [];

  return (
    <>
      <div className="libraryToolbar gapToolbar">
        <strong>{pageMeta.total} Gap Records</strong>
        <input className="librarySearch compact" value={keyword} placeholder="Search gaps..." onChange={(event) => onKeywordChange(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') onSearch(); }} />
        <select aria-label="Filter gaps by category" value={category} onChange={(event) => onCategoryChange(event.target.value)}><option value="">All categories</option>{['Auth / JWT', 'Redis Cache', 'PostgreSQL', 'Docker', 'Kubernetes', 'Incident SOP', 'Ticket Rules', 'Customer Templates', 'General'].map((item) => <option key={item}>{item}</option>)}</select>
        <select aria-label="Filter gaps by review status" value={status} onChange={(event) => onStatusChange(event.target.value)}><option value="">All status</option><option value="OPEN">Open</option><option value="REVIEWED">Reviewed</option></select>
      </div>
      <div className="actionRecordGrid">
      {gaps.length ? gaps.map((gap) => (
        <button type="button" key={`${gap.logId}-${gap.question}`} onClick={() => onView(gap)}>
          <div>
            <strong>{gap.question}</strong>
            <span>{gap.category} · Frequency {gap.frequency}</span>
          </div>
          <p>{gap.suggestedArticle || gap.answer || 'No reviewed answer yet.'}</p>
          <b className={`priorityPill ${gap.priority?.toLowerCase()}`}>{gap.priority || 'Review'}</b>
          <em onClick={(event) => { event.stopPropagation(); onDelete(gap); }}>Delete</em>
        </button>
      )) : <EmptyState text="No matching knowledge gaps." />}
      </div>
      <ReviewPager page={pageMeta.page} pageCount={pageMeta.pageCount} total={pageMeta.total} onPageChange={onPageChange} showSinglePage />
    </>
  );
}

function Tickets({ data }) {
  const rows = (data?.tickets ?? []).map((ticket) => [
    ticket.title,
    <span className={`priorityPill ${ticket.priority?.toLowerCase()}`}>{ticket.priority}</span>,
    ticket.createdAgo,
    ticket.owner,
    ticket.sla,
  ]);

  return <Table columns={['Title', 'Priority', 'Created', 'Owner', 'SLA']} rows={rows} empty="No open escalation tickets." />;
}

function Analytics({ data }) {
  const categories = data?.topCategoriesByQuestions ?? [];

  return (
    <div className="analyticsGrid">
      <div className="usefulnessBlock">
        <div className="donutRing muted" style={{ '--value': `${Math.max(data?.usefulnessPercent ?? 0, 8)}%` }}>
          <b>{data?.usefulnessPercent ?? 0}%</b>
          <span>Useful</span>
        </div>
        <div>
          <p><i className="greenDot" />Useful <b>{data?.usefulCount ?? 0}</b></p>
          <p><i className="goldDot" />Partially Useful <b>{data?.partiallyUsefulCount ?? 0}</b></p>
          <p><i className="redDot" />Not Useful <b>{data?.notUsefulCount ?? 0}</b></p>
        </div>
      </div>
      <div className="trendBlock">
        <h3>Coverage Trend</h3>
        <div className="trendLine"><span style={{ width: `${Math.max(data?.coverageTrend ?? 0, 10)}%` }} /></div>
        <b>{data?.coverageTrend ?? 0}%</b>
      </div>
      <div>
        <h3>Top Categories by Questions</h3>
        {categories.length ? categories.map((category) => (
          <div className="categoryBar" key={category.category}>
            <span>{category.category}</span>
            <div><i style={{ width: `${category.percent}%` }} /></div>
            <b>{category.percent}%</b>
          </div>
        )) : <EmptyState text="No question category data yet." />}
      </div>
    </div>
  );
}

function ModelControls({ data }) {
  const profile = data?.modelProfile ?? {};
  const retrieval = data?.retrievalStrategy ?? {};
  const web = data?.webSearchSettings ?? {};

  return (
    <div className="controlsGrid">
      <InfoGroup title="Model Profile" rows={[
        ['Primary Model', profile.primaryModel],
        ['Embedding Model', profile.embeddingModel],
        ['Reranker Model', profile.rerankerModel],
        ['Status', statusText(profile.status)],
      ]} />
      <InfoGroup title="Retrieval Strategy" rows={[
        ['Top-K Documents', retrieval.topKDocuments],
        ['Top-K Articles', retrieval.topKArticles],
        ['Top-K Web Evidence', retrieval.topKWebEvidence],
        ['Reranker Enabled', retrieval.rerankerEnabled ? 'On' : 'Off'],
      ]} />
      <InfoGroup title="Web Search Settings" rows={[
        ['Web Fallback Threshold', web.webFallbackThreshold],
        ['Trusted Domains', web.trustedDomains],
        ['Max Web Results', web.maxWebResults],
      ]} />
    </div>
  );
}

function InfoGroup({ title, rows }) {
  return (
    <div className="infoGroup">
      <h3>{title}</h3>
      {rows.map(([label, value]) => (
        <p key={label}><span>{label}</span><b>{value ?? '-'}</b></p>
      ))}
    </div>
  );
}

function Table({ columns, rows, empty }) {
  return (
    <div className="adminTableWrap">
      <table className="adminTable">
        <thead>
          <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          {rows.length ? rows.map((row, rowIndex) => (
            <tr key={`${rowIndex}-${row[0]}`}>
              {row.map((cell, cellIndex) => <td key={`${cellIndex}-${columns[cellIndex]}`}>{cell}</td>)}
            </tr>
          )) : (
            <tr>
              <td colSpan={columns.length}><EmptyState text={empty} /></td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString();
}

function formatFileSize(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 KB';
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function buildNotificationKeys(gaps = [], tickets = [], evidence = [], documentCandidates = []) {
  const gapKeys = gaps.map((gap) => `gap:${gap.logId ?? gap.id ?? gap.question}`);
  const ticketKeys = tickets.map((ticket) => `ticket:${ticket.ticketId ?? ticket.id ?? ticket.title}`);
  const evidenceKeys = evidence.map((item) => `evidence:${item.evidenceId ?? item.id ?? item.queryTopic ?? item.question}`);
  const documentCandidateKeys = documentCandidates.map((item) => `document-candidate:${item.id ?? item.title}`);
  return [...gapKeys, ...ticketKeys, ...evidenceKeys, ...documentCandidateKeys].filter(Boolean);
}

function KnowledgeContentBlock({ content }) {
  const lines = String(content || '').split(/\r?\n/);
  return (
    <div className="knowledgeContentBlock">
      {lines.map((line, index) => {
        const trimmed = line.trim();
        if (!trimmed) {
          return <div className="knowledgeSpacer" key={`space-${index}`} />;
        }
        if (trimmed.startsWith('#')) {
          return <h3 key={`heading-${index}`}>{trimmed.replace(/^#+\s*/, '')}</h3>;
        }
        if (/^(Purpose|Category|Primary keywords|Source references|Fast triage checklist|Common root causes|Recovery actions|Escalate immediately when|Prevention controls|Resolution notes|Impact model|Useful commands|Operational checks|Decision model|Triage checklist|Knowledge follow-up|Long-term target):?/i.test(trimmed)) {
          return <div className="knowledgeSignal" key={`signal-${index}`}>{trimmed.replace(/:$/, '')}</div>;
        }
        if (/^[-*]\s+/.test(trimmed)) {
          return <p className="knowledgeBullet" key={`bullet-${index}`}>{trimmed.replace(/^[-*]\s+/, '')}</p>;
        }
        if (/^\d+\.\s+/.test(trimmed)) {
          return <p className="knowledgeStep" key={`step-${index}`}><b>{trimmed.match(/^\d+/)?.[0]}</b>{trimmed.replace(/^\d+\.\s+/, '')}</p>;
        }
        if (/^```/.test(trimmed)) {
          return <div className="knowledgeCodeFence" key={`code-${index}`}>Command / SQL block</div>;
        }
        return <p key={`p-${index}`}>{trimmed}</p>;
      })}
    </div>
  );
}

function KnowledgeReader({ article, chunks, actions }) {
  if (!article && !chunks?.length) {
    return <EmptyState text="Select an item to preview its stored knowledge." />;
  }

  if (article) {
    return (
      <article className="knowledgeReader">
        <div className="knowledgeReaderHeader">
          <span>ARTICLE</span>
          <strong>{article.title}</strong>
          <small>{article.category || 'Uncategorized'} · {formatDateTime(article.updatedTime || article.createdTime)}</small>
          {actions && <div className="readerActions">{actions}</div>}
        </div>
        <KnowledgeContentBlock content={article.content} />
      </article>
    );
  }

  return (
    <div className="knowledgeReader">
      <div className="knowledgeReaderHeader">
        <span>DOCUMENT CHUNKS</span>
        <strong>{chunks.length} searchable chunks</strong>
        <small>These are the exact passages used by document retrieval.</small>
        {actions && <div className="readerActions">{actions}</div>}
      </div>
      <div className="chunkList">
        {chunks.map((chunk) => (
          <article key={chunk.id || chunk.chunkIndex}>
            <b>Chunk {chunk.chunkIndex}</b>
            <KnowledgeContentBlock content={chunk.content} />
          </article>
        ))}
      </div>
    </div>
  );
}

function AdminModal({ title, children, onClose }) {
  return (
    <div className="adminModalOverlay" role="dialog" aria-modal="true">
      <div className="adminModal">
        <div className="adminModalHeader">
          <h2>{title}</h2>
          <button type="button" onClick={onClose}>Close</button>
        </div>
        {children}
      </div>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="adminFormField">
      <span>{label}</span>
      {children}
    </label>
  );
}

function CapabilityNotice({ title, description }) {
  return (
    <div className="capabilityNotice">
      <AlertTriangle size={22} />
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
    </div>
  );
}

function AdminOperationsCenter() {
  const [adminRoute, setAdminRoute] = useState(() => parseAdminRoute());
  const [data, setData] = useState(fallbackData);
  const [loadState, setLoadState] = useState('loading');
  const [activeModal, setActiveModal] = useState(null);
  const [actionStatus, setActionStatus] = useState({ type: '', text: '' });
  const [articleForm, setArticleForm] = useState({ title: '', category: 'General', file: null });
  const [documentForm, setDocumentForm] = useState({ category: 'General', file: null });
  const [questionForm, setQuestionForm] = useState({ question: '', file: null });
  const [questionAnswer, setQuestionAnswer] = useState(null);
  const [assistantQuestion, setAssistantQuestion] = useState('');
  const [answerGroundingOpen, setAnswerGroundingOpen] = useState(false);
  const [articles, setArticles] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [detailArticle, setDetailArticle] = useState(null);
  const [detailDocument, setDetailDocument] = useState(null);
  const [detailChunks, setDetailChunks] = useState([]);
  const [detailState, setDetailState] = useState('idle');
  const [detailError, setDetailError] = useState('');
  const [adminChunks, setAdminChunks] = useState([]);
  const [selectedAdminChunk, setSelectedAdminChunk] = useState(null);
  const [documentCandidates, setDocumentCandidates] = useState([]);
  const [selectedDocumentCandidate, setSelectedDocumentCandidate] = useState(null);
  const [articleSearch, setArticleSearch] = useState('');
  const [articleCategory, setArticleCategory] = useState('');
  const [documentSearch, setDocumentSearch] = useState('');
  const [documentCategory, setDocumentCategory] = useState('');
  const [chunkSearch, setChunkSearch] = useState('');
  const [candidateSearch, setCandidateSearch] = useState('');
  const [candidateStatus, setCandidateStatus] = useState('NEEDS_REVIEW');
  const [candidateCategory, setCandidateCategory] = useState('');
  const [reviewQueueTab, setReviewQueueTab] = useState('steward');
  const [stewardTasks, setStewardTasks] = useState([]);
  const [selectedStewardTask, setSelectedStewardTask] = useState(null);
  const [stewardCategory, setStewardCategory] = useState('');
  const [stewardPageInfo, setStewardPageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [stewardPage, setStewardPage] = useState(1);
  const [stewardRunning, setStewardRunning] = useState(false);
  const [articlePage, setArticlePage] = useState(1);
  const [documentPage, setDocumentPage] = useState(1);
  const [chunkPage, setChunkPage] = useState(1);
  const [candidatePage, setCandidatePage] = useState(1);
  const [aiLogPage, setAiLogPage] = useState(1);
  const [ticketPage, setTicketPage] = useState(1);
  const [webEvidencePage, setWebEvidencePage] = useState(1);
  const [gapPage, setGapPage] = useState(1);
  const [articlePageInfo, setArticlePageInfo] = useState(() => pageInfo(null, LIBRARY_PAGE_SIZE));
  const [documentPageInfo, setDocumentPageInfo] = useState(() => pageInfo(null, LIBRARY_PAGE_SIZE));
  const [chunkPageInfo, setChunkPageInfo] = useState(() => pageInfo(null, LIBRARY_PAGE_SIZE));
  const [candidatePageInfo, setCandidatePageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [aiLogPageInfo, setAiLogPageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [ticketPageInfo, setTicketPageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [webEvidencePageInfo, setWebEvidencePageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [gapPageInfo, setGapPageInfo] = useState(() => pageInfo(null, REVIEW_PAGE_SIZE));
  const [globalSearch, setGlobalSearch] = useState('');
  const [globalSearchResults, setGlobalSearchResults] = useState({ articles: [], chunks: [] });
  const [articleEditor, setArticleEditor] = useState({ id: null, title: '', category: '', content: '' });
  const [chunkEditor, setChunkEditor] = useState({ id: null, content: '' });
  const [reviewEditor, setReviewEditor] = useState({ type: '', id: null, title: '', category: '', content: '' });
  const [aiLogs, setAiLogs] = useState([]);
  const [selectedAiLog, setSelectedAiLog] = useState(null);
  const [knowledgeGaps, setKnowledgeGaps] = useState([]);
  const [selectedGap, setSelectedGap] = useState(null);
  const [gapArticleDraft, setGapArticleDraft] = useState({ title: '', category: '', content: '' });
  const [tickets, setTickets] = useState([]);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [ticketStatus, setTicketStatus] = useState('OPEN');
  const [ticketListStatus, setTicketListStatus] = useState('');
  const [ticketPriority, setTicketPriority] = useState('');
  const [notificationSnapshot, setNotificationSnapshot] = useState({ gaps: [], tickets: [], evidence: [], documentCandidates: [] });
  const [webEvidence, setWebEvidence] = useState([]);
  const [webEvidenceSummary, setWebEvidenceSummary] = useState(null);
  const [selectedEvidence, setSelectedEvidence] = useState(null);
  const [webEvidenceSearch, setWebEvidenceSearch] = useState('');
  const [webEvidenceStatus, setWebEvidenceStatus] = useState('');
  const [webEvidenceOrigin, setWebEvidenceOrigin] = useState('');
  const [webEvidenceFilter, setWebEvidenceFilter] = useState('');
  const [webEvidenceCategory, setWebEvidenceCategory] = useState('');
  const [gapSearch, setGapSearch] = useState('');
  const [gapCategory, setGapCategory] = useState('');
  const [gapStatus, setGapStatus] = useState('');
  const [aiLogSearch, setAiLogSearch] = useState('');
  const [aiLogSource, setAiLogSource] = useState('');
  const [aiLogFeedback, setAiLogFeedback] = useState('');
  const [ticketSearch, setTicketSearch] = useState('');
  const [profile, setProfile] = useState(null);
  const [metricContext, setMetricContext] = useState(null);
  const [llmStatus, setLlmStatus] = useState(null);
  const [activePage, setActivePage] = useState(() => parseAdminRoute().page);
  const [readNotificationKeys, setReadNotificationKeys] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('aegiscloud_read_notifications') || '[]');
    } catch {
      return [];
    }
  });
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [knowledgeNavOpen, setKnowledgeNavOpen] = useState(true);
  const [settingsNavOpen, setSettingsNavOpen] = useState(true);
  const [assistantFullscreen, setAssistantFullscreen] = useState(false);
  const user = useMemo(() => {
    try {
      return JSON.parse(localStorage.getItem('aegiscloud_user') || '{}');
    } catch {
      return {};
    }
  }, []);

  const navigateAdmin = useCallback((path, replace = false) => {
    const nextPath = normalizeAdminPath(path);
    if (replace) window.history.replaceState({}, '', nextPath);
    else window.history.pushState({}, '', nextPath);
    const nextRoute = parseAdminRoute(nextPath);
    setAdminRoute(nextRoute);
    setActivePage(nextRoute.page);
    setActiveModal(null);
    setActionStatus({ type: '', text: '' });
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      const nextRoute = parseAdminRoute();
      setAdminRoute(nextRoute);
      setActivePage(nextRoute.page);
      setActiveModal(null);
      setActionStatus({ type: '', text: '' });
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const loadOperationsCenter = useCallback(() => {
    setLoadState('loading');
    getOperationsCenter()
      .then((result) => {
        if (!result) throw new Error('Operations API returned no dashboard data.');
        setData(result);
        setLoadState('live');
        getDocumentKnowledgeCandidates()
          .then((candidateResult) => {
            const loadedCandidates = pageItems(candidateResult);
            setDocumentCandidates(loadedCandidates);
            setCandidatePageInfo(pageInfo(candidateResult, REVIEW_PAGE_SIZE));
            setSelectedDocumentCandidate((current) => current && loadedCandidates.find((item) => item.id === current.id)
              ? loadedCandidates.find((item) => item.id === current.id)
              : current);
          })
          .catch(() => {
            setDocumentCandidates([]);
          });
      })
      .catch(() => {
        setData(fallbackData);
        setLoadState('fallback');
      });
  }, []);

  const pagedArticles = articles;
  const pagedDocuments = documents;
  const pagedAdminChunks = adminChunks;
  const pagedDocumentCandidates = documentCandidates;
  const pagedAiLogs = aiLogs;
  const conversationLogs = useMemo(() => {
    const seen = new Set();
    return pagedAiLogs.filter((log) => {
      const key = String(log?.question || '').trim().toLowerCase();
      if (!key || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [pagedAiLogs]);

  const dashboardNotificationKeys = useMemo(() => buildNotificationKeys(
    data.knowledgeGapLoop?.gaps ?? [],
    data.ticketEscalation?.tickets ?? [],
    data.externalEvidence?.queue ?? [],
    documentCandidates.filter((candidate) => candidate.status === 'NEEDS_REVIEW')
  ), [data.knowledgeGapLoop?.gaps, data.ticketEscalation?.tickets, data.externalEvidence?.queue, documentCandidates]);

  const unreadNotificationCount = useMemo(
    () => dashboardNotificationKeys.filter((key) => !readNotificationKeys.includes(key)).length,
    [dashboardNotificationKeys, readNotificationKeys]
  );

  const markNotificationsRead = (keys) => {
    const nextKeys = Array.from(new Set([...readNotificationKeys, ...keys]));
    setReadNotificationKeys(nextKeys);
    localStorage.setItem('aegiscloud_read_notifications', JSON.stringify(nextKeys));
  };

  useEffect(() => {
    loadOperationsCenter();
  }, [loadOperationsCenter]);

  useEffect(() => {
    if (activeModal !== 'modelStatus' && activePage !== 'settings') {
      return;
    }
    getLlmGatewayStatus()
      .then(setLlmStatus)
      .catch(() => setLlmStatus(null));
  }, [activeModal, activePage]);

  const openModal = (modal) => {
    setActionStatus({ type: '', text: '' });
    if (modal === 'ask') {
      setQuestionAnswer(null);
      setAnswerGroundingOpen(false);
    }
    setActiveModal(modal);
  };

  const loadArticles = async (page = articlePage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading stored knowledge articles...' });
    try {
      const result = await getKnowledgeArticles({ page, size: LIBRARY_PAGE_SIZE, keyword: filters.keyword ?? articleSearch, category: filters.category ?? articleCategory, status: 'PUBLISHED' });
      const items = pageItems(result);
      setArticles(items);
      setArticlePageInfo(pageInfo(result, LIBRARY_PAGE_SIZE));
      setArticlePage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const loadAiLogs = async (page = aiLogPage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading real AI question records...' });
    try {
      const result = await getAiQuestionLogs({ page, size: REVIEW_PAGE_SIZE, keyword: filters.keyword ?? aiLogSearch, sourceType: filters.sourceType ?? aiLogSource, feedback: filters.feedback ?? aiLogFeedback });
      setAiLogs(pageItems(result));
      setAiLogPageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setAiLogPage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setAiLogs([]);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openAiLogs = async (context = 'questions') => {
    openModal('aiLogs');
    setMetricContext(context);
    await loadAiLogs();
  };

  const loadTickets = async (page = ticketPage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading real ticket queue...' });
    try {
      const result = await getTickets({ page, size: REVIEW_PAGE_SIZE, keyword: filters.keyword ?? ticketSearch, status: filters.status ?? ticketListStatus, priority: filters.priority ?? ticketPriority });
      setTickets(pageItems(result));
      setTicketPageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setTicketPage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setTickets([]);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openTickets = async () => {
    openModal('ticketList');
    await loadTickets();
  };

  const loadWebEvidence = async (page = webEvidencePage, status = webEvidenceStatus, origin = webEvidenceOrigin, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading external web evidence queue...' });
    try {
      const [resultResponse, summaryResponse] = await Promise.allSettled([
        getWebEvidence({ page, size: REVIEW_PAGE_SIZE, keyword: filters.keyword ?? webEvidenceFilter, category: filters.category ?? webEvidenceCategory, status, origin }),
        getWebEvidenceSummary(),
      ]);
      if (resultResponse.status === 'rejected') throw resultResponse.reason;
      const result = resultResponse.value;
      const loadedEvidence = pageItems(result);
      setWebEvidence(loadedEvidence);
      setWebEvidenceSummary(summaryResponse.status === 'fulfilled' ? summaryResponse.value : null);
      setWebEvidencePageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setWebEvidencePage(result?.page ?? page);
      setSelectedEvidence((current) => current && loadedEvidence.find((item) => item.id === current.id)
        ? loadedEvidence.find((item) => item.id === current.id)
        : loadedEvidence[0] ?? null);
      setActionStatus(summaryResponse.status === 'fulfilled'
        ? { type: '', text: '' }
        : { type: 'error', text: 'Evidence loaded, but aggregate metrics are unavailable. Restart the backend to load the new summary API.' });
    } catch (error) {
      setWebEvidence([]);
      setWebEvidenceSummary(null);
      setSelectedEvidence(null);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openNotifications = async () => {
    openModal('notifications');
    setActionStatus({ type: 'loading', text: 'Loading real admin notifications...' });
    try {
      const [gapResults, ticketResults, evidenceResults, documentCandidateResults] = await Promise.all([
        getKnowledgeGaps(),
        getTickets(),
        getWebEvidence(),
        getDocumentKnowledgeCandidates(),
      ]);
      const gapItems = pageItems(gapResults);
      const ticketItems = pageItems(ticketResults);
      const evidenceItems = pageItems(evidenceResults);
      const candidateItems = pageItems(documentCandidateResults);
      const unreadGaps = gapItems.filter((gap) => !readNotificationKeys.includes(buildNotificationKeys([gap], [], [])[0]));
      const unreadTickets = ticketItems
        .filter((ticket) => ['OPEN', 'IN_PROGRESS'].includes(ticket.status))
        .filter((ticket) => !readNotificationKeys.includes(buildNotificationKeys([], [ticket], [])[0]));
      const unreadEvidence = evidenceItems
        .filter((item) => item.status === 'NEEDS_REVIEW')
        .filter((item) => !readNotificationKeys.includes(buildNotificationKeys([], [], [item])[0]));
      const unreadDocumentCandidates = candidateItems
        .filter((item) => item.status === 'NEEDS_REVIEW')
        .filter((item) => !readNotificationKeys.includes(buildNotificationKeys([], [], [], [item])[0]));
      setKnowledgeGaps(gapItems);
      setTickets(ticketItems);
      setWebEvidence(evidenceItems);
      setDocumentCandidates(candidateItems);
      setNotificationSnapshot({ gaps: unreadGaps, tickets: unreadTickets, evidence: unreadEvidence, documentCandidates: unreadDocumentCandidates });
      setSelectedEvidence(unreadEvidence[0] ?? evidenceItems[0] ?? null);
      setSelectedDocumentCandidate(unreadDocumentCandidates[0] ?? candidateItems[0] ?? null);
      markNotificationsRead(buildNotificationKeys(unreadGaps, unreadTickets, unreadEvidence, unreadDocumentCandidates));
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setKnowledgeGaps([]);
      setTickets([]);
      setWebEvidence([]);
      setDocumentCandidates([]);
      setNotificationSnapshot({ gaps: [], tickets: [], evidence: [], documentCandidates: [] });
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const loadKnowledgeGaps = async (page = gapPage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading knowledge gaps...' });
    try {
      const result = await getKnowledgeGaps({ page, size: REVIEW_PAGE_SIZE, keyword: filters.keyword ?? gapSearch, category: filters.category ?? gapCategory, status: filters.status ?? gapStatus });
      const items = pageItems(result);
      setKnowledgeGaps(items);
      setGapPageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setGapPage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setKnowledgeGaps([]);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openProfile = async () => {
    openModal('profile');
    setActionStatus({ type: 'loading', text: 'Loading account details...' });
    try {
      const result = await getCurrentUser();
      setProfile(result ?? user);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setProfile(user);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const handleLogout = async () => {
    setActionStatus({ type: 'loading', text: 'Signing out...' });
    try {
      await logoutCurrentUser();
    } catch {
      // Clear the local session even when the backend token is already expired.
    }
    localStorage.removeItem('aegiscloud_token');
    localStorage.removeItem('aegiscloud_user');
    localStorage.removeItem('aegiscloud_entry');
    window.history.replaceState({}, '', '/');
    window.location.reload();
  };

  const loadDocuments = async (page = documentPage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading uploaded documents...' });
    try {
      const result = await getKnowledgeDocuments({ page, size: LIBRARY_PAGE_SIZE, keyword: filters.keyword ?? documentSearch, category: filters.category ?? documentCategory, status: 'ACTIVE' });
      const loadedDocuments = pageItems(result);
      setDocuments(loadedDocuments);
      setDocumentPageInfo(pageInfo(result, LIBRARY_PAGE_SIZE));
      setDocumentPage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const loadAdminChunks = async (page = chunkPage) => {
    setActionStatus({ type: 'loading', text: 'Loading searchable retrieval chunks...' });
    try {
      const result = await getAdminChunks({ page, size: LIBRARY_PAGE_SIZE, keyword: chunkSearch, status: 'ACTIVE' });
      const items = pageItems(result);
      setAdminChunks(items);
      setChunkPageInfo(pageInfo(result, LIBRARY_PAGE_SIZE));
      setChunkPage(result?.page ?? page);
      setSelectedAdminChunk((current) => current && items.find((item) => item.id === current.id)
        ? items.find((item) => item.id === current.id)
        : items[0] ?? null);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setAdminChunks([]);
      setSelectedAdminChunk(null);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openRetrievalIndex = async () => {
    openModal('retrievalIndex');
    await loadAdminChunks(1);
  };

  const loadDocumentCandidates = async (page = candidatePage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading document learning candidates...' });
    try {
      const result = await getDocumentKnowledgeCandidates({ page, size: REVIEW_PAGE_SIZE, keyword: filters.keyword ?? candidateSearch, status: filters.status ?? candidateStatus, category: filters.category ?? candidateCategory });
      const loadedCandidates = pageItems(result);
      setDocumentCandidates(loadedCandidates);
      setCandidatePageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setCandidatePage(result?.page ?? page);
      setSelectedDocumentCandidate((current) => current && loadedCandidates.find((item) => item.id === current.id)
        ? loadedCandidates.find((item) => item.id === current.id)
        : loadedCandidates[0] ?? null);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setDocumentCandidates([]);
      setSelectedDocumentCandidate(null);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const loadStewardTasks = async (page = stewardPage, filters = {}) => {
    setActionStatus({ type: 'loading', text: 'Loading AI knowledge curation tasks...' });
    try {
      const result = await getKnowledgeStewardTasks({
        page,
        size: REVIEW_PAGE_SIZE,
        status: 'NEEDS_ADMIN_REVIEW',
        targetDomain: filters.category ?? stewardCategory,
      });
      const items = pageItems(result);
      setStewardTasks(items);
      setSelectedStewardTask((current) => current && items.some((item) => item.id === current.id)
        ? items.find((item) => item.id === current.id)
        : items[0] ?? null);
      setStewardPageInfo(pageInfo(result, REVIEW_PAGE_SIZE));
      setStewardPage(result?.page ?? page);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setStewardTasks([]);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const runKnowledgeCuration = async () => {
    if (!window.confirm('Run the AI Knowledge Steward on pending evidence and answer logs? This may use model tokens for eligible tasks.')) return;
    setStewardRunning(true);
    setActionStatus({ type: 'loading', text: 'The AI Knowledge Steward is evaluating pending knowledge...' });
    try {
      const result = await runKnowledgeSteward(20);
      await loadStewardTasks(1);
      setActionStatus({
        type: 'success',
        text: `Curation finished. ${result?.createdTasks ?? 0} review task(s) created; ${result?.llmCallsAllowed ?? 0} model call(s) allowed. Nothing was published automatically.`,
      });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    } finally {
      setStewardRunning(false);
    }
  };

  const reviewStewardTask = async (action, task = selectedStewardTask) => {
    if (!task) return;
    if (action === 'approve' && !window.confirm(`Approve the curated proposal "${task.proposalTitle}" and reorganize the target Knowledge Pack?`)) return;
    if (action === 'reject' && !window.confirm('Reject this AI-curated knowledge proposal?')) return;
    setActionStatus({ type: 'loading', text: `${action === 'approve' ? 'Publishing reviewed knowledge' : 'Rejecting proposal'}...` });
    try {
      if (action === 'approve') await approveKnowledgeStewardTask(task.id);
      else if (action === 'reject') await rejectKnowledgeStewardTask(task.id);
      else await archiveKnowledgeStewardTask(task.id);
      await loadStewardTasks(stewardPage);
      setActionStatus({ type: 'success', text: action === 'approve' ? 'Knowledge approved, reorganized, and re-indexed.' : `Proposal ${action}d.` });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  useEffect(() => {
    const isArticleDetail = ['knowledgePackDetail', 'articleDetail'].includes(adminRoute.page);
    const isDocumentDetail = adminRoute.page === 'documentDetail';
    if (!isArticleDetail && !isDocumentDetail) {
      setDetailState('idle');
      setDetailError('');
      return undefined;
    }

    let active = true;
    setDetailState('loading');
    setDetailError('');
    setDetailArticle(null);
    setDetailDocument(null);
    setDetailChunks([]);

    const load = async () => {
      try {
        if (isArticleDetail) {
          const article = await getKnowledgeArticle(adminRoute.id);
          if (!active) return;
          setDetailArticle(article);
          setDetailState('ready');
          if (adminRoute.mode === 'edit') {
            setArticleEditor({
              id: article.id,
              title: article.title || '',
              category: article.category || '',
              content: article.content || '',
            });
            setActiveModal('editArticle');
          }
          return;
        }

        const [document, chunks] = await Promise.all([
          getKnowledgeDocument(adminRoute.id),
          getDocumentChunks(adminRoute.id),
        ]);
        if (!active) return;
        setDetailDocument(document);
        setDetailChunks(chunks || []);
        setDetailState('ready');
      } catch (error) {
        if (!active) return;
        setDetailState('error');
        setDetailError(error.message || 'The requested resource could not be loaded.');
      }
    };
    load();
    return () => { active = false; };
  }, [adminRoute]);

  useEffect(() => {
    const loaders = {
      knowledge: () => loadArticles(1),
      documents: () => loadDocuments(1),
      documentLearning: () => { loadDocumentCandidates(1); loadStewardTasks(1); },
      webEvidence: () => loadWebEvidence(1),
      ai: () => loadAiLogs(1),
      gaps: () => loadKnowledgeGaps(1),
      tickets: () => loadTickets(1),
      assistant: () => loadAiLogs(1),
    };
    loaders[adminRoute.page]?.();
  }, [adminRoute.page]);

  const submitGlobalSearch = async (event) => {
    event.preventDefault();
    const keyword = globalSearch.trim();
    if (!keyword) {
      setActionStatus({ type: 'error', text: 'Enter a keyword to search stored knowledge.' });
      openModal('globalSearch');
      return;
    }

    openModal('globalSearch');
    setActionStatus({ type: 'loading', text: 'Searching articles and uploaded document chunks...' });
    try {
      const [articleResults, chunkResults] = await Promise.all([
        searchKnowledgeArticles(keyword),
        searchDocumentChunks(keyword),
      ]);
      setGlobalSearchResults({
        articles: articleResults ?? [],
        chunks: chunkResults ?? [],
      });
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setGlobalSearchResults({ articles: [], chunks: [] });
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openSearchArticle = (article) => {
    closeModal();
    navigateAdmin(`/admin/knowledge/articles/${article.id}`);
  };

  const openSearchChunk = (chunk) => {
    const selected = {
      id: chunk.chunkId,
      documentId: chunk.documentId,
      originalFilename: chunk.originalFilename,
      category: chunk.category,
      chunkIndex: chunk.chunkIndex,
      content: chunk.content,
      status: 'ACTIVE',
    };
    setAdminChunks([selected]);
    setSelectedAdminChunk(selected);
    setChunkPageInfo({ page: 1, size: 1, total: 1, pageCount: 1 });
    setChunkSearch('');
    setActionStatus({ type: '', text: '' });
    setActiveModal('retrievalIndex');
  };

  const openChunkEditor = (chunk) => {
    if (!chunk) return;
    setChunkEditor({
      id: chunk.id,
      content: chunk.content || '',
    });
    openModal('editChunk');
  };

  const openReviewEditor = (type, item) => {
    if (!item) return;
    setReviewEditor({
      type,
      id: item.id,
      title: type === 'webEvidence' ? item.suggestedArticleTitle || item.title || '' : item.title || '',
      category: item.category || 'General',
      content: type === 'webEvidence'
        ? item.suggestedArticleContent || item.extractedContent || ''
        : item.content || '',
    });
    openModal('editReviewContent');
  };

  const submitReviewEdit = async (event) => {
    event.preventDefault();
    if (!reviewEditor.id || !reviewEditor.title.trim() || !reviewEditor.content.trim()) {
      setActionStatus({ type: 'error', text: 'Title and content are required before saving review content.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Saving reviewed knowledge content...' });
    try {
      const payload = {
        title: reviewEditor.title.trim(),
        category: reviewEditor.category.trim(),
        content: reviewEditor.content.trim(),
      };
      if (reviewEditor.type === 'webEvidence') {
        const updated = await updateWebEvidenceSuggestion(reviewEditor.id, payload);
        setSelectedEvidence(updated);
        await loadWebEvidence();
      } else {
        const updated = await updateDocumentKnowledgeCandidate(reviewEditor.id, payload);
        setSelectedDocumentCandidate(updated);
        await loadDocumentCandidates();
      }
      setActionStatus({ type: 'success', text: 'Review content saved. It is ready for approval.' });
      setActiveModal(null);
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const copyToClipboard = async (text, successText) => {
    try {
      await navigator.clipboard.writeText(text || '');
      setActionStatus({ type: 'success', text: successText });
    } catch {
      setActionStatus({ type: 'error', text: 'Clipboard permission was not available. Select and copy the text manually.' });
    }
  };

  const closeModal = () => {
    setActiveModal(null);
    setActionStatus({ type: '', text: '' });
  };

  const handleNavAction = (label) => {
    const actions = {
      command: () => {
        navigateAdmin('/admin');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      assistant: async () => {
        navigateAdmin('/admin/assistant');
      },
      knowledge: async () => {
        navigateAdmin('/admin/knowledge');
      },
      documents: async () => {
        navigateAdmin('/admin/sources');
      },
      documentLearning: async () => {
        navigateAdmin('/admin/review');
      },
      webEvidence: async () => {
        navigateAdmin('/admin/web-evidence');
      },
      ai: async () => {
        navigateAdmin('/admin/ai-activity');
      },
      gaps: async () => {
        navigateAdmin('/admin/gaps');
      },
      tickets: async () => {
        navigateAdmin('/admin/tickets');
      },
      users: () => navigateAdmin('/admin/users'),
      settings: () => navigateAdmin('/admin/settings'),
    };
    setMobileSidebarOpen(false);
    actions[label]?.();
  };

  const openGlobalSearch = () => {
    setGlobalSearchResults({ articles: [], chunks: [] });
    setActionStatus({ type: '', text: '' });
    setActiveModal('globalSearch');
  };

  useEffect(() => {
    const handleGlobalShortcut = (event) => {
      if (event.key === 'Escape') {
        setMobileSidebarOpen(false);
        setAssistantFullscreen(false);
        return;
      }
      const target = event.target;
      const isTyping = target instanceof HTMLElement
        && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
      if (!isTyping && ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k')) {
        event.preventDefault();
        openGlobalSearch();
      }
    };
    window.addEventListener('keydown', handleGlobalShortcut);
    return () => window.removeEventListener('keydown', handleGlobalShortcut);
  }, []);

  const handleMetricClick = (metricKey) => {
    const actions = {
      totalKnowledgeArticles: async () => {
        navigateAdmin('/admin/knowledge');
        await loadArticles();
      },
      runbookDocuments: async () => {
        navigateAdmin('/admin/sources');
        await loadDocuments();
      },
      knowledgeCoverage: () => openModal('coverageDetail'),
      questionsAnswered7d: async () => {
        navigateAdmin('/admin/ai-activity');
        await loadAiLogs();
      },
      pendingReviews: async () => {
        navigateAdmin('/admin/review');
        await loadDocumentCandidates(1);
      },
      avgAnswerConfidence: async () => {
        navigateAdmin('/admin/ai-activity');
        await loadAiLogs();
      },
      openTickets: async () => {
        navigateAdmin('/admin/tickets');
        await loadTickets();
      },
    };
    actions[metricKey]?.();
  };

  const openWebEvidencePage = async () => {
    navigateAdmin('/admin/web-evidence');
    await loadWebEvidence();
  };

  const viewWebEvidenceFromSummary = async (evidenceId) => {
    navigateAdmin('/admin/web-evidence');
    setActionStatus({ type: 'loading', text: 'Opening external evidence record...' });
    try {
      const result = await getWebEvidence();
      const loadedEvidence = pageItems(result);
      setWebEvidence(loadedEvidence);
      setSelectedEvidence(loadedEvidence.find((item) => item.id === evidenceId) ?? loadedEvidence[0] ?? null);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitWebEvidenceSearch = async (event) => {
    event.preventDefault();
    const question = webEvidenceSearch.trim();
    if (!question) {
      setActionStatus({ type: 'error', text: 'Enter a CloudOps question before searching the web.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Searching the web and saving evidence for review...' });
    try {
      const evidence = await searchWebEvidence(question);
      setWebEvidence((current) => [evidence, ...current.filter((item) => item.id !== evidence.id)]);
      setSelectedEvidence(evidence);
      setWebEvidenceSearch('');
      setWebEvidenceStatus('');
      await loadWebEvidence(1, '', webEvidenceOrigin);
      setSelectedEvidence(evidence);
      setActionStatus({ type: 'success', text: 'External evidence saved to the review queue.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const approveSelectedEvidence = async (evidence) => {
    if (!evidence) return;
    const confirmed = window.confirm(`Convert "${evidence.suggestedArticleTitle || evidence.title}" into a published knowledge article?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Converting web evidence into an internal knowledge article...' });
    try {
      const article = await approveWebEvidence(evidence.id);
      await loadWebEvidence();
      setActionStatus({ type: 'success', text: `Published article: ${article.title}` });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const rejectSelectedEvidence = async (evidence) => {
    if (!evidence) return;
    const confirmed = window.confirm(`Reject web evidence from "${evidence.domain || 'this source'}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Rejecting external evidence...' });
    try {
      await rejectWebEvidence(evidence.id);
      await loadWebEvidence();
      setActionStatus({ type: 'success', text: 'Evidence rejected and removed from the pending queue.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedEvidence = async (evidence) => {
    if (!evidence) return;
    const confirmed = window.confirm(`Archive web evidence "${evidence.title || evidence.question}"? It will be hidden from the active review queue.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving external evidence...' });
    try {
      await archiveWebEvidence(evidence.id);
      await loadWebEvidence();
      setActionStatus({ type: 'success', text: 'Evidence archived.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const deleteSelectedEvidence = async (evidence) => {
    if (!evidence) return;
    const confirmed = window.confirm(`Permanently delete rejected evidence "${evidence.title || evidence.question}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Deleting rejected evidence...' });
    try {
      await deleteWebEvidence(evidence.id);
      await loadWebEvidence();
      setActionStatus({ type: 'success', text: 'Rejected evidence deleted.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const approveSelectedDocumentCandidate = async (candidate) => {
    if (!candidate) return;
    const reorganizesExisting = ['REORGANIZE_KNOWLEDGE', 'MERGE_ARTICLE'].includes(candidate.suggestedAction);
    const confirmed = window.confirm(reorganizesExisting
      ? `Reorganize "${candidate.suggestedArticleTitle || 'the closest Knowledge Pack'}" with this reviewed knowledge?`
      : `Create a new Knowledge Pack from "${candidate.title}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Reorganizing durable knowledge...' });
    try {
      const article = await approveDocumentKnowledgeCandidate(candidate.id);
      await loadDocumentCandidates();
      setActionStatus({ type: 'success', text: `Knowledge Pack updated: ${article.title}` });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const rejectSelectedDocumentCandidate = async (candidate) => {
    if (!candidate) return;
    const confirmed = window.confirm(`Reject knowledge candidate "${candidate.title}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Rejecting knowledge candidate...' });
    try {
      await rejectDocumentKnowledgeCandidate(candidate.id);
      await loadDocumentCandidates();
      setActionStatus({ type: 'success', text: 'Knowledge candidate rejected.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedDocumentCandidate = async (candidate) => {
    if (!candidate) return;
    const confirmed = window.confirm(`Archive knowledge candidate "${candidate.title}"? It will be hidden from the active review queue.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving knowledge candidate...' });
    try {
      await archiveDocumentKnowledgeCandidate(candidate.id);
      await loadDocumentCandidates();
      setActionStatus({ type: 'success', text: 'Knowledge candidate archived.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const deleteSelectedDocumentCandidate = async (candidate) => {
    if (!candidate) return;
    const confirmed = window.confirm(`Permanently delete rejected knowledge candidate "${candidate.title}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Deleting rejected knowledge candidate...' });
    try {
      await deleteDocumentKnowledgeCandidate(candidate.id);
      await loadDocumentCandidates();
      setActionStatus({ type: 'success', text: 'Rejected knowledge candidate deleted.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openAiLogDetail = (log) => {
    setSelectedAiLog(log);
    openModal('aiLogDetail');
  };

  const deleteSelectedAiLog = async (log = selectedAiLog) => {
    if (!log) return;
    const confirmed = window.confirm(`Delete AI log for "${log.question}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Deleting AI question log...' });
    try {
      await deleteAiQuestionLog(log.id);
      setAiLogs((current) => current.filter((item) => item.id !== log.id));
      if (selectedAiLog?.id === log.id) {
        setSelectedAiLog(null);
        if (activeModal === 'aiLogDetail') {
          setActiveModal(null);
        }
      }
      setActionStatus({ type: 'success', text: 'AI question log deleted.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openGapDetail = async (gap) => {
    setSelectedGap(gap);
    setGapArticleDraft({
      title: gap.suggestedArticle || `${gap.category || 'General'}: ${gap.question}`,
      category: gap.category || 'General',
      content: buildGapArticleDraft(gap),
    });
    openModal('gapDetail');

    try {
      const result = await getKnowledgeGaps();
      const fullGap = pageItems(result).find((item) => item.logId === gap.logId);
      if (fullGap) {
        const mergedGap = { ...gap, ...fullGap };
        setSelectedGap(mergedGap);
        setGapArticleDraft({
          title: gap.suggestedArticle || `${gap.category || 'General'}: ${gap.question}`,
          category: gap.category || 'General',
          content: buildGapArticleDraft(mergedGap),
        });
      }
    } catch {
      // Keep the operations-center summary gap if the full gap list cannot be loaded.
    }
  };

  const buildGapArticleDraft = (gap) => `# ${gap.suggestedArticle || gap.question}

Category: ${gap.category || 'General'}

Purpose:
Document a reliable internal answer for a repeated AI miss.

Original question:
${gap.question}

Observed answer:
${gap.answer || 'Add the final reviewed answer here before publishing.'}

Resolution steps:
- Confirm the production context and affected component.
- Add concrete checks, commands, dashboards, or logs that support teams should use.
- Define escalation ownership and expected recovery signal.

Prevention controls:
- Add monitoring, alerting, or runbook links that reduce repeat questions.
`;

  const submitGapArticle = async (event) => {
    event.preventDefault();
    if (!selectedGap) return;
    if (!gapArticleDraft.title.trim() || !gapArticleDraft.content.trim()) {
      setActionStatus({ type: 'error', text: 'Title and content are required before publishing.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Publishing knowledge gap as an internal article...' });
    try {
      const article = await createArticleFromKnowledgeGap(selectedGap.logId, {
        title: gapArticleDraft.title.trim(),
        category: gapArticleDraft.category.trim() || 'General',
        content: gapArticleDraft.content.trim(),
      });
      setActionStatus({ type: 'success', text: `Published article: ${article.title}` });
      await loadOperationsCenter();
      setActiveModal(null);
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const deleteSelectedGap = async (gap = selectedGap) => {
    if (!gap) return;
    const confirmed = window.confirm(`Delete knowledge gap "${gap.question}"?`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Deleting knowledge gap...' });
    try {
      await deleteKnowledgeGap(gap.logId);
      await loadKnowledgeGaps(gapPage);
      setSelectedGap(null);
      setActiveModal(null);
      setActionStatus({ type: 'success', text: 'Knowledge gap deleted.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openTicketDetail = async (ticket) => {
    const ticketId = ticket.id || ticket.ticketId;
    if (!ticketId) return;
    setSelectedTicket(ticket);
    setTicketStatus(ticket.status || 'OPEN');
    openModal('ticketDetail');
    setActionStatus({ type: 'loading', text: 'Loading ticket detail...' });
    try {
      const detail = await getTicketDetail(ticketId);
      setSelectedTicket(detail);
      setTicketStatus(detail.status || 'OPEN');
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitTicketStatus = async (event) => {
    event.preventDefault();
    if (!selectedTicket) return;
    setActionStatus({ type: 'loading', text: 'Updating ticket status...' });
    try {
      await updateTicketStatus(selectedTicket.id, ticketStatus);
      const detail = await getTicketDetail(selectedTicket.id);
      setSelectedTicket(detail);
      setTicketStatus(detail.status || ticketStatus);
      await loadTickets();
      loadOperationsCenter();
      setActionStatus({ type: 'success', text: 'Ticket status updated.' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const resolveSelectedTicket = async () => {
    if (!selectedTicket) return;
    setActionStatus({ type: 'loading', text: 'Resolving ticket...' });
    try {
      await resolveTicket(selectedTicket.id);
      const detail = await getTicketDetail(selectedTicket.id);
      setSelectedTicket(detail);
      setTicketStatus(detail.status || 'RESOLVED');
      await loadTickets();
      loadOperationsCenter();
      setActionStatus({ type: 'success', text: 'Ticket resolved.' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const closeSelectedTicket = async () => {
    if (!selectedTicket) return;
    setActionStatus({ type: 'loading', text: 'Closing ticket...' });
    try {
      await closeTicket(selectedTicket.id);
      const detail = await getTicketDetail(selectedTicket.id);
      setSelectedTicket(detail);
      setTicketStatus(detail.status || 'CLOSED');
      await loadTickets();
      loadOperationsCenter();
      setActionStatus({ type: 'success', text: 'Ticket closed.' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitArticleUpload = async (event) => {
    event.preventDefault();
    if (!articleForm.file) {
      setActionStatus({ type: 'error', text: 'Choose a .txt, .md, .pdf, or .docx article file first.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Uploading article and saving it to the knowledge base...' });
    try {
      await uploadKnowledgeArticle(articleForm);
      setActionStatus({ type: 'success', text: 'Article saved to the knowledge base.' });
      setArticleForm({ title: '', category: 'General', file: null });
      setArticles([]);
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitDocumentUpload = async (event) => {
    event.preventDefault();
    if (!documentForm.file) {
      setActionStatus({ type: 'error', text: 'Choose a .txt, .md, .pdf, or .docx document file first.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Uploading document, extracting text, and creating chunks...' });
    try {
      await uploadKnowledgeDocument(documentForm);
      setActionStatus({ type: 'success', text: 'Document uploaded and indexed for AI retrieval.' });
      setDocumentForm({ category: 'General', file: null });
      setDocuments([]);
      setSelectedChunks([]);
      await loadDocumentCandidates();
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitAiQuestion = async (event) => {
    event.preventDefault();
    if (!questionForm.question.trim()) {
      setActionStatus({ type: 'error', text: 'Enter a question before asking AI.' });
      return;
    }

    const submittedQuestion = questionForm.question.trim();
    const submittedFile = questionForm.file;
    setAssistantQuestion(submittedQuestion);
    setQuestionAnswer(null);
    setSelectedAiLog(null);
    setActionStatus({ type: 'loading', text: submittedFile ? 'Uploading file, searching precise knowledge, and asking AI...' : 'Asking AI and recording the question log...' });
    try {
      const answer = await askAiQuestion({ question: submittedQuestion, file: submittedFile });
      const answerWithQuestion = { ...answer, question: submittedQuestion };
      setQuestionAnswer(answerWithQuestion);
      setAnswerGroundingOpen(false);
      if (hasUsableAnswer(answer)) {
        const successText = submittedFile ? 'Answer generated from the uploaded file and recorded.' : 'Answer generated and recorded.';
        setActionStatus({ type: 'success', text: successText });
        window.setTimeout(() => setActionStatus((current) => current.text === successText ? { type: '', text: '' } : current), 3500);
        setQuestionForm({ question: '', file: null });
      } else {
        setActionStatus({ type: 'error', text: answerRouteLabel(answer) + '. Your question is preserved so you can retry or refine it.' });
        setQuestionForm((current) => ({ ...current, question: submittedQuestion }));
      }
      if (activePage === 'ai') {
        await loadAiLogs();
      }
      if (questionForm.file) {
        await loadDocumentCandidates();
      }
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitArticleEdit = async (event) => {
    event.preventDefault();
    if (!articleEditor.title.trim() || !articleEditor.content.trim()) {
      setActionStatus({ type: 'error', text: 'Title and content are required.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Saving article changes...' });
    try {
      const updated = await updateKnowledgeArticle(articleEditor.id, {
        title: articleEditor.title.trim(),
        category: articleEditor.category.trim() || null,
        content: articleEditor.content.trim(),
      });
      setArticles((current) => current.map((article) => article.id === updated.id ? updated : article));
      setDetailArticle((current) => current?.id === updated.id ? updated : current);
      setActionStatus({ type: 'success', text: 'Article updated and ready for AI retrieval.' });
      if (['knowledgePackDetail', 'articleDetail'].includes(adminRoute.page)) {
        setActiveModal(null);
        navigateAdmin(adminRoute.page === 'knowledgePackDetail'
          ? `/admin/knowledge/packs/${updated.id}`
          : `/admin/knowledge/articles/${updated.id}`, true);
      } else {
        closeModal();
      }
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const submitChunkEdit = async (event) => {
    event.preventDefault();
    if (!chunkEditor.content.trim()) {
      setActionStatus({ type: 'error', text: 'Chunk content is required.' });
      return;
    }

    setActionStatus({ type: 'loading', text: 'Saving retrieval chunk...' });
    try {
      const updated = await updateAdminChunk(chunkEditor.id, {
        content: chunkEditor.content.trim(),
      });
      setAdminChunks((current) => current.map((chunk) => chunk.id === updated.id ? updated : chunk));
      setSelectedAdminChunk(updated);
      setActiveModal('retrievalIndex');
      await loadAdminChunks(chunkPage);
      setActionStatus({ type: 'success', text: 'Retrieval unit updated. Future searches will use the revised passage.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedArticle = async (article) => {
    if (!article) return;
    const confirmed = window.confirm(`Archive article "${article.title}"? It will no longer be used as an AI answer source.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving article...' });
    try {
      await archiveKnowledgeArticle(article.id);
      const remaining = articles.filter((item) => item.id !== article.id);
      setArticles(remaining);
      setDetailArticle(null);
      setActionStatus({ type: 'success', text: 'Article archived. It is removed from active AI retrieval.' });
      if (['knowledgePackDetail', 'articleDetail'].includes(adminRoute.page)) {
        navigateAdmin('/admin/knowledge');
      }
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedDocument = async (document) => {
    if (!document) return;
    const confirmed = window.confirm(`Archive document "${document.originalFilename}"? Its chunks will no longer be used by AI retrieval.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving document...' });
    try {
      await archiveKnowledgeDocument(document.id);
      const remaining = documents.filter((item) => item.id !== document.id);
      setDocuments(remaining);
      setDetailDocument(null);
      setDetailChunks([]);
      setActionStatus({ type: 'success', text: 'Document archived. Its chunks are removed from active retrieval.' });
      if (adminRoute.page === 'documentDetail') {
        navigateAdmin('/admin/sources');
      }
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedChunk = async () => {
    if (!selectedAdminChunk) return;
    const confirmed = window.confirm(`Archive chunk ${selectedAdminChunk.chunkIndex} from "${selectedAdminChunk.originalFilename}"? It will no longer be used by AI retrieval.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving retrieval chunk...' });
    try {
      await archiveAdminChunk(selectedAdminChunk.id);
      await loadAdminChunks(chunkPage);
      setActionStatus({ type: 'success', text: 'Retrieval unit archived and removed from active search.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const rebuildLibraryQuality = async () => {
    const confirmed = window.confirm('Rebuild published Knowledge Packs and active retrieval chunks now? This will archive duplicate articles and replace active document chunks with freshly generated semantic chunks.');
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Rebuilding Knowledge Packs and semantic chunks...' });
    try {
      const result = await rebuildKnowledgeLibrary();
      await Promise.all([
        loadArticles(1),
        loadAdminChunks(1),
      ]);
      loadOperationsCenter();
      setActionStatus({
        type: 'success',
        text: `Rebuild complete: ${result.articlesRewritten} articles rewritten, ${result.duplicateArticlesArchived} duplicates archived, ${result.chunksCreated} chunks created.`,
      });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const renderCommandCenterPage = () => (
    <>
      <section className="metricsGrid dashboardMetrics">
        {[
          ...(data.metrics ?? []).filter((metric) => ['totalKnowledgeArticles', 'questionsAnswered7d', 'openTickets'].includes(metric.key)),
          { key: 'pendingReviews', label: 'Pending Reviews', value: documentCandidates.filter((candidate) => candidate.status === 'NEEDS_REVIEW').length + numberValue(data.externalEvidence?.summary?.pendingReview), detail: 'File and web evidence queue', icon: 'target', trend: '', trendType: 'neutral' },
        ].sort((left, right) => ['totalKnowledgeArticles', 'questionsAnswered7d', 'pendingReviews', 'openTickets'].indexOf(left.key) - ['totalKnowledgeArticles', 'questionsAnswered7d', 'pendingReviews', 'openTickets'].indexOf(right.key)).map((metric) => <MetricCard metric={metric} key={metric.key} onClick={() => handleMetricClick(metric.key)} />)}
      </section>

      <section className="dashboardAskPanel">
        <div className="dashboardAskCopy">
          <span className="panelKicker">AI ASSISTANT</span>
          <h2>Ask Aegis AI</h2>
          <p>Get grounded answers from internal knowledge, uploaded documents, and trusted web sources.</p>
          <div className="aiRouteChips"><span>Internal Knowledge</span><span>Web Search</span><span>AI Analysis</span></div>
        </div>
        <form className="dashboardAskForm" onSubmit={submitAiQuestion}>
          <textarea value={questionForm.question} rows={3} placeholder="Type your question here..." aria-label="Ask Aegis AI" onChange={(event) => setQuestionForm((current) => ({ ...current, question: event.target.value }))} />
          <div className="dashboardAskActions">
            <label className="attachAction"><Upload size={15} />Attach file<input type="file" accept=".txt,.md,.pdf,.docx" onChange={(event) => setQuestionForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))} /></label>
            <button type="button" onClick={openGlobalSearch}><Search size={15} />Search knowledge</button>
            <button type="button" onClick={openWebEvidencePage}><Globe2 size={15} />Use web search</button>
            <button className="primaryAction" type="submit">Send <ChevronDown size={15} /></button>
          </div>
          {actionStatus.text && activePage === 'command' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          {questionAnswer && <div className={`dashboardAnswerPreview ${!hasUsableAnswer(questionAnswer) ? 'answerUnavailable' : ''}`}><strong>{answerRouteLabel(questionAnswer)} · {answerEngineLabel(questionAnswer)}</strong><KnowledgeContentBlock content={questionAnswer.answer || 'No reliable answer was generated.'} /><button type="button" onClick={() => { setActivePage('assistant'); setSelectedAiLog(questionAnswer); setAssistantQuestion(questionAnswer.question || ''); }}>Open in AI Assistant</button></div>}
        </form>
      </section>

      <section className="dashboardOperationsGrid">
        <article className="insightPanel">
          <div className="insightHeader">
            <span>Knowledge Overview</span>
            <button type="button" onClick={() => openModal('coverageDetail')}>Details</button>
          </div>
          <div className="overviewMetrics"><div><strong>{formatCount(data.metrics?.find((metric) => metric.key === 'totalKnowledgeArticles')?.value)}</strong><span>Articles</span></div><div><strong>{formatCount(data.metrics?.find((metric) => metric.key === 'runbookDocuments')?.value)}</strong><span>Documents</span></div><div><strong>{data.metrics?.find((metric) => metric.key === 'knowledgeCoverage')?.value ?? '—'}</strong><span>Coverage</span></div></div>
          <div className="miniCoverageList">
            {(data.knowledgeCore?.categories ?? []).slice(0, 5).map((category) => (
              <div key={category.category}>
                <p><strong>{category.category}</strong><b>{category.coveragePercent ?? 0}%</b></p>
                <i><em style={{ width: `${category.coveragePercent ?? 0}%` }} /></i>
              </div>
            ))}
          </div>
        </article>

        <article className="insightPanel">
          <div className="insightHeader">
            <span>Action Required</span>
            <button type="button" onClick={openNotifications}>View all</button>
          </div>
          <div className="attentionList">
            <button type="button" onClick={openWebEvidencePage}><strong>{data.externalEvidence?.summary?.pendingReview ?? 0}</strong><span>Pending web evidence reviews</span><ChevronDown size={15} /></button>
            {(data.knowledgeGapLoop?.gaps ?? []).slice(0, 2).map((gap) => (
              <button type="button" key={gap.logId} onClick={async () => { setActivePage('gaps'); await loadKnowledgeGaps(1); }}>
                <strong>{gap.priority}</strong>
                <span>{gap.question}</span>
                <ChevronDown size={15} />
              </button>
            ))}
            {(data.ticketEscalation?.tickets ?? []).slice(0, 2).map((ticket) => (
              <button type="button" key={ticket.ticketId} onClick={async () => { setActivePage('tickets'); await loadTickets(); }}>
                <strong>{ticket.priority}</strong>
                <span>{ticket.title}</span>
                <ChevronDown size={15} />
              </button>
            ))}
            {!(data.knowledgeGapLoop?.gaps ?? []).length && !(data.ticketEscalation?.tickets ?? []).length && <EmptyState text="No urgent work items." />}
          </div>
        </article>

      </section>

      <section className="dashboardActivityPanel">
        <div className="insightHeader"><span>Recent AI Activity</span><button type="button" onClick={() => handleNavAction('ai')}>View all</button></div>
        {pagedAiLogs.length ? <div className="activityTable"><div className="activityTableHead"><span>Question</span><span>Answer mode</span><span>Status</span><span>Confidence</span><span>Time</span></div>{pagedAiLogs.slice(0, 5).map((log) => <button type="button" key={log.id} onClick={() => openAiLogDetail(log)}><span>{log.question}</span><b>{statusText(log.answerMode || log.sourceType)}</b><i className="statusPill success">Success</i><span>{log.confidence ?? '—'}</span><small>{formatDateTime(log.createdTime)}</small></button>)}</div> : <EmptyState text="No AI activity has been recorded yet." />}
      </section>
    </>
  );

  const renderDetailState = (resourceLabel) => (
    <section className="adminPageSurface detailPageSurface">
      <div className="detailState">
        {detailState === 'loading' && <RefreshCw size={22} className="loadingIcon" />}
        {detailState === 'error' && <AlertTriangle size={22} />}
        <h2>{detailState === 'loading' ? `Loading ${resourceLabel}...` : 'Resource unavailable'}</h2>
        <p>{detailState === 'loading' ? 'The page is loading the current record from the backend.' : detailError || 'The requested record does not exist or you do not have permission to view it.'}</p>
        {detailState === 'error' && <button type="button" onClick={() => navigateAdmin(resourceLabel === 'document' ? '/admin/sources' : '/admin/knowledge')}>Back to list</button>}
      </div>
    </section>
  );

  const renderArticleDetailPage = () => {
    if (detailState !== 'ready' || !detailArticle) return renderDetailState('knowledge article');
    const isPack = adminRoute.page === 'knowledgePackDetail';
    const listPath = '/admin/knowledge';
    return (
      <section className="adminPageSurface detailPageSurface">
        <div className="detailBreadcrumb"><button type="button" onClick={() => navigateAdmin(listPath)}>Knowledge Library</button><span>/</span><button type="button" onClick={() => navigateAdmin(`/admin/knowledge/packs/${detailArticle.id}`)}>Knowledge Packs</button><span>/</span><strong>{detailArticle.title}</strong></div>
        <div className="detailPageHeader">
          <div><span>{isPack ? 'KNOWLEDGE PACK' : 'ARTICLE'}</span><h2>{detailArticle.title}</h2><p>{detailArticle.category || 'Uncategorized'} · {statusText(detailArticle.status)} · Updated {formatDateTime(detailArticle.updatedTime || detailArticle.createdTime)}</p></div>
          <div className="detailPageActions"><button type="button" onClick={() => navigateAdmin(listPath)}>Back to Knowledge</button><button type="button" onClick={() => navigateAdmin(isPack ? `/admin/knowledge/packs/${detailArticle.id}/edit` : `/admin/knowledge/articles/${detailArticle.id}/edit`)}>Edit</button><button type="button" onClick={() => copyToClipboard(detailArticle.content, 'Article content copied.')}>Copy</button><button className="danger" type="button" onClick={() => archiveSelectedArticle(detailArticle)}>Archive</button></div>
        </div>
        <div className="detailMetaGrid"><div><span>Category</span><strong>{detailArticle.category || 'Uncategorized'}</strong></div><div><span>Status</span><strong>{statusText(detailArticle.status)}</strong></div><div><span>Last updated</span><strong>{formatDateTime(detailArticle.updatedTime || detailArticle.createdTime)}</strong></div><div><span>Retrieval</span><strong>{detailArticle.status === 'PUBLISHED' ? 'Available to AI' : 'Not active'}</strong></div></div>
        <div className="detailContentGrid"><article className="detailContentPanel"><div className="detailPanelHeader"><span>Knowledge Content</span><small>Published content used as an internal answer source.</small></div><KnowledgeContentBlock content={detailArticle.content} /></article><aside className="detailSidePanel"><div className="detailPanelHeader"><span>Related Information</span><small>Only backend-supported metadata is shown.</small></div><dl><div><dt>Article ID</dt><dd>{detailArticle.id}</dd></div><div><dt>Created</dt><dd>{formatDateTime(detailArticle.createdTime)}</dd></div><div><dt>Created by</dt><dd>{detailArticle.createdBy ?? '—'}</dd></div></dl><div className="detailSideActions"><button type="button" onClick={() => navigateAdmin(`/admin/knowledge/articles/${detailArticle.id}`)}>Open article view</button></div></aside></div>
      </section>
    );
  };

  const renderDocumentDetailPage = () => {
    if (detailState !== 'ready' || !detailDocument) return renderDetailState('document');
    const tab = adminRoute.tab || 'overview';
    return (
      <section className="adminPageSurface detailPageSurface">
        <div className="detailBreadcrumb"><button type="button" onClick={() => navigateAdmin('/admin/sources')}>Source Library</button><span>/</span><button type="button" onClick={() => navigateAdmin('/admin/sources')}>Documents</button><span>/</span><strong>{detailDocument.originalFilename}</strong></div>
        <div className="detailPageHeader"><div><span>SOURCE DOCUMENT</span><h2>{detailDocument.originalFilename}</h2><p>{String(detailDocument.contentType || 'Document').split('/').pop().toUpperCase()} · {statusText(detailDocument.status)} · Uploaded {formatDateTime(detailDocument.createdTime)}</p></div><div className="detailPageActions"><button type="button" onClick={() => navigateAdmin('/admin/sources')}>Back to Sources</button><button className="danger" type="button" onClick={() => archiveSelectedDocument(detailDocument)}>Archive</button></div></div>
        <div className="detailMetaGrid"><div><span>File type</span><strong>{detailDocument.contentType || '—'}</strong></div><div><span>Size</span><strong>{formatFileSize(detailDocument.fileSize)}</strong></div><div><span>Category</span><strong>{detailDocument.category || 'Uncategorized'}</strong></div><div><span>Chunks</span><strong>{detailChunks.length}</strong></div><div><span>Uploaded by</span><strong>{detailDocument.uploadedBy ?? '—'}</strong></div><div><span>Updated</span><strong>{formatDateTime(detailDocument.updatedTime || detailDocument.createdTime)}</strong></div></div>
        <div className="detailTabs" role="tablist"><button className={tab === 'overview' ? 'active' : ''} type="button" onClick={() => navigateAdmin(`/admin/sources/documents/${detailDocument.id}`)}>Overview</button><button className={tab === 'chunks' ? 'active' : ''} type="button" onClick={() => navigateAdmin(`/admin/sources/documents/${detailDocument.id}/chunks`)}>Chunks</button><button className="disabledTab" type="button" disabled>Metadata</button><button className="disabledTab" type="button" disabled>Retrieval</button></div>
        {tab === 'chunks' ? <KnowledgeReader chunks={detailChunks} actions={<button type="button" onClick={() => copyToClipboard(detailChunks.map((chunk) => chunk.content).join('\n\n'), 'Document chunks copied.')}>Copy Chunks</button>} /> : <div className="detailContentGrid"><article className="detailContentPanel"><div className="detailPanelHeader"><span>Preview</span><small>Extracted content is available through the document chunks.</small></div>{detailChunks.length ? <KnowledgeContentBlock content={detailChunks.slice(0, 3).map((chunk) => chunk.content).join('\n\n')} /> : <EmptyState text="No searchable chunks are available for this document." />}</article><aside className="detailSidePanel"><div className="detailPanelHeader"><span>Index Status</span><small>Current document processing state.</small></div><dl><div><dt>Document status</dt><dd>{statusText(detailDocument.status)}</dd></div><div><dt>Searchable chunks</dt><dd>{detailChunks.length}</dd></div><div><dt>Embedding status</dt><dd>{detailChunks.length ? 'Recorded' : 'Not available'}</dd></div></dl><div className="detailSideActions"><button type="button" onClick={() => navigateAdmin(`/admin/sources/documents/${detailDocument.id}/chunks`)}>View all chunks</button></div></aside></div>}
      </section>
    );
  };

  const renderKnowledgePage = () => (
    <section className="adminPageSurface libraryListPage">
      <div className="adminPageHeader">
        <div>
          <span>Knowledge Library</span>
          <h2>Knowledge Packs</h2>
          <p>Organize and maintain reviewed knowledge packs and articles used by AegisCloud.</p>
        </div>
        <div>
          <button className="primaryAction" type="button" onClick={() => openModal('uploadArticle')}>+ New Knowledge Pack</button>
        </div>
      </div>
      <div className="libraryTabs"><button className="active" type="button">Knowledge Packs</button><button type="button" onClick={() => openModal('coverageDetail')}>Categories</button></div>
      <div className="knowledgeLibrary knowledgePackLayout">
        <div className="libraryList libraryListOnly">
          <div className="libraryFilterBar"><input className="librarySearch" value={articleSearch} placeholder="Search knowledge packs..." onChange={(event) => setArticleSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadArticles(1); }} /><select aria-label="Category filter" value={articleCategory} onChange={(event) => { setArticleCategory(event.target.value); loadArticles(1, { category: event.target.value }); }}><option value="">All Categories</option>{[...Object.keys(categoryIconMap), 'General'].map((category) => <option key={category} value={category}>{category}</option>)}</select><span className="filterContext">Published knowledge</span><button type="button" onClick={() => loadArticles(1)}>Apply</button></div>
          {actionStatus.text && activePage === 'knowledge' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          {pagedArticles.length ? pagedArticles.map((article) => (
            <button className="knowledgePackRow" type="button" key={article.id} onClick={() => navigateAdmin(`/admin/knowledge/packs/${article.id}`)}>
              <span className="packIcon"><BookOpen size={17} /></span><span className="packSummary"><strong>{article.title}</strong><small>{(article.content || '').split(/\r?\n/).find(Boolean)?.slice(0, 120) || 'Reviewed knowledge pack'}</small><em>{article.category || 'Uncategorized'} · Article · Updated {formatDateTime(article.updatedTime || article.createdTime)}</em></span><b>{statusText(article.status)}</b><span className="rowAction">View</span><span className="rowAction moreAction">•••</span>
            </button>
          )) : <EmptyState text="No knowledge articles uploaded yet." />}
          <LibraryPager page={articlePageInfo.page} pageCount={articlePageInfo.pageCount} total={articlePageInfo.total} onPageChange={loadArticles} />
        </div>
      </div>
    </section>
  );

  const renderDocumentPage = () => (
    <section className="adminPageSurface libraryListPage">
      <div className="adminPageHeader">
        <div>
          <span>Source Library</span>
          <h2>Documents</h2>
          <p>Manage uploaded documents and retrieval sources.</p>
        </div>
        <div>
          <button className="primaryAction" type="button" onClick={() => openModal('uploadDocument')}>+ Upload Document</button>
        </div>
      </div>
      <div className="libraryTabs"><button className="active" type="button">Documents</button><button type="button" onClick={openWebEvidencePage}>Web Sources</button></div>
      <div className="knowledgeLibrary sourceTableLayout">
        <div className="libraryList libraryListOnly">
          <div className="libraryFilterBar"><input className="librarySearch" value={documentSearch} placeholder="Search documents..." onChange={(event) => setDocumentSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadDocuments(1); }} /><select aria-label="Document category filter" value={documentCategory} onChange={(event) => { setDocumentCategory(event.target.value); loadDocuments(1, { category: event.target.value }); }}><option value="">All Categories</option>{[...Object.keys(categoryIconMap), 'General'].map((category) => <option key={category} value={category}>{category}</option>)}</select><span className="filterContext">Active source documents</span><button type="button" onClick={() => loadDocuments(1)}>Apply</button></div>
          <div className="sourceTableHead"><span>Name</span><span>Type</span><span>Source</span><span>Chunks</span><span>Status</span><span>Updated</span><span>Actions</span></div>
          {actionStatus.text && activePage === 'documents' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          {pagedDocuments.length ? pagedDocuments.map((document) => (
            <button className="sourceTableRow" type="button" key={document.id} onClick={() => navigateAdmin(`/admin/sources/documents/${document.id}`)}>
              <strong><FileText size={15} />{document.originalFilename}</strong><span>{String(document.contentType || document.originalFilename || '').split('.').pop().toUpperCase()}</span><span>User Upload</span><span>{document.chunkCount ?? document.chunks ?? '—'}</span><b>{statusText(document.status)}</b><span>{formatDateTime(document.updatedTime || document.createdTime)}</span><span className="rowAction moreAction">•••</span>
            </button>
          )) : <EmptyState text="No documents uploaded yet." />}
          <LibraryPager page={documentPageInfo.page} pageCount={documentPageInfo.pageCount} total={documentPageInfo.total} onPageChange={loadDocuments} />
        </div>
      </div>
    </section>
  );

  const renderDocumentLearningPage = () => {
    const isReorganizeCandidate = (candidate) => ['REORGANIZE_KNOWLEDGE', 'MERGE_ARTICLE'].includes(candidate?.suggestedAction);
    return (
      <section className="adminPageSurface reviewAdminPage">
        <div className="adminPageHeader">
          <div>
            <span>Knowledge Review</span>
            <h2>Review knowledge before publishing</h2>
            <p>Review AI-curated proposals and file-derived knowledge before publishing it to the Knowledge Library.</p>
          </div>
        </div>
        <div className="reviewQueueTabs" role="tablist" aria-label="Knowledge review queues">
          <button type="button" role="tab" aria-selected={reviewQueueTab === 'steward'} className={reviewQueueTab === 'steward' ? 'active' : ''} onClick={() => setReviewQueueTab('steward')}>AI Steward <span>{stewardPageInfo.total}</span></button>
          <button type="button" role="tab" aria-selected={reviewQueueTab === 'files'} className={reviewQueueTab === 'files' ? 'active' : ''} onClick={() => setReviewQueueTab('files')}>File Knowledge <span>{candidatePageInfo.total}</span></button>
        </div>
        {reviewQueueTab === 'steward' ? (
        <section className="stewardWorkspace" role="tabpanel" aria-label="AI Steward review queue">
          <div className="stewardWorkspaceHeader">
            <div><span>AI KNOWLEDGE STEWARD</span><h3>Curate evidence into review-ready knowledge</h3><p>Evaluates pending web evidence, uploaded-file insights, and eligible AI answers. It never publishes without administrator approval.</p></div>
            <div className="stewardRunControl">
              <div className="stewardRunButtons"><button type="button" aria-label="Refresh AI Steward queue" onClick={() => loadStewardTasks(1)}><RefreshCw size={14} /></button><button type="button" className="primaryAction" disabled={stewardRunning} onClick={runKnowledgeCuration}>{stewardRunning ? 'Curating...' : 'Run Knowledge Curation'}</button></div>
              {stewardRunning && <span role="status">Evaluating eligible evidence. The request is still running; duplicate runs are disabled.</span>}
            </div>
          </div>
          <div className="stewardWorkspaceGrid">
            <div className="stewardTaskList">
              <strong>{stewardPageInfo.total} Pending Steward Tasks</strong>
              <select aria-label="Filter steward tasks by category" value={stewardCategory} onChange={(event) => { const category = event.target.value; setStewardCategory(category); loadStewardTasks(1, { category }); }}><option value="">All categories</option>{['Auth / JWT', 'Redis Cache', 'PostgreSQL', 'Docker', 'Kubernetes', 'Incident SOP', 'Ticket Rules', 'Customer Templates', 'General'].map((category) => <option key={category}>{category}</option>)}</select>
              {stewardTasks.length ? stewardTasks.map((task) => <button className={selectedStewardTask?.id === task.id ? 'active' : ''} type="button" key={task.id} onClick={() => setSelectedStewardTask(task)}><b>{task.proposalTitle}</b><span>{task.sourceType} · {task.targetDomain} · {statusText(task.recommendedAction)}</span></button>) : <EmptyState text="No AI-curated tasks are waiting. Run curation after new evidence or answers have been recorded." />}
              <ReviewPager page={stewardPageInfo.page} pageCount={stewardPageInfo.pageCount} total={stewardPageInfo.total} onPageChange={loadStewardTasks} showSinglePage />
            </div>
            <div className="stewardProposal">
              {selectedStewardTask ? <>
                <div className="stewardProposalHeader"><div><span>{selectedStewardTask.targetDomain} · {statusText(selectedStewardTask.recommendedAction)}</span><h4>{selectedStewardTask.proposalTitle}</h4></div><div><button type="button" onClick={() => reviewStewardTask('approve')}>Approve & Reorganize</button><button className="danger" type="button" onClick={() => reviewStewardTask('reject')}>Reject</button></div></div>
                <p><b>Target:</b> {selectedStewardTask.targetArticleTitle || 'Create a new Knowledge Pack'}{selectedStewardTask.targetSection ? ` · ${selectedStewardTask.targetSection}` : ''}</p>
                {selectedStewardTask.warnings && <div className="adminActionStatus error">{selectedStewardTask.warnings}</div>}
                <KnowledgeContentBlock content={selectedStewardTask.proposalContent} />
                {selectedStewardTask.sourceExcerpt && <details><summary>Source evidence</summary><KnowledgeContentBlock content={selectedStewardTask.sourceExcerpt} /></details>}
              </> : <EmptyState text="Select a task to inspect the proposal and its source evidence." />}
            </div>
          </div>
        </section>
        ) : (
        <div className="knowledgeLibrary embedded reviewCandidatesWorkspace" role="tabpanel" aria-label="File knowledge review queue">
          <div className="libraryList">
            <div className="libraryToolbar">
              <strong>{candidatePageInfo.total} Review Candidates</strong>
              <div className="reviewQueueActions"><button type="button" onClick={() => loadDocumentCandidates(1)}><RefreshCw size={14} />Refresh</button><button className="primaryAction" type="button" onClick={() => openModal('ask')}>Ask With File</button></div>
            </div>
            <div className="libraryFilterBar"><input className="librarySearch" value={candidateSearch} placeholder="Search review candidates..." onChange={(event) => setCandidateSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadDocumentCandidates(1); }} /><select aria-label="Review status" value={candidateStatus} onChange={(event) => { const status = event.target.value; setCandidateStatus(status); loadDocumentCandidates(1, { status }); }}><option value="">All statuses</option><option value="NEEDS_REVIEW">Needs review</option><option value="CONVERTED">Approved</option><option value="REJECTED">Rejected</option><option value="ARCHIVED">Archived</option></select><select aria-label="Review category" value={candidateCategory} onChange={(event) => { const category = event.target.value; setCandidateCategory(category); loadDocumentCandidates(1, { category }); }}><option value="">All categories</option>{['Auth / JWT', 'Redis Cache', 'PostgreSQL', 'Docker', 'Kubernetes', 'Incident SOP', 'Ticket Rules', 'Customer Templates', 'General'].map((category) => <option key={category}>{category}</option>)}</select></div>
            {actionStatus.text && activePage === 'documentLearning' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            {pagedDocumentCandidates.length ? pagedDocumentCandidates.map((candidate) => (
              <button
                className={selectedDocumentCandidate?.id === candidate.id ? 'active' : ''}
                type="button"
                key={candidate.id}
                onClick={() => setSelectedDocumentCandidate(candidate)}
              >
                <strong>{candidate.title}</strong>
                <span>{candidate.category || 'General'} / {statusText(candidate.status)} / {isReorganizeCandidate(candidate) ? `Reorganize ${candidate.suggestedArticleTitle || 'Knowledge Pack'}` : 'New Knowledge Pack'} / {candidate.originalFilename}</span>
              </button>
            )) : <EmptyState text="No knowledge review candidates yet. Ask AI with a CloudOps file to create reviewable insights." />}
            <ReviewPager page={candidatePageInfo.page} pageCount={candidatePageInfo.pageCount} total={candidatePageInfo.total} onPageChange={loadDocumentCandidates} showSinglePage />
          </div>
          <KnowledgeReader
            article={selectedDocumentCandidate && {
              title: selectedDocumentCandidate.title,
              category: selectedDocumentCandidate.category,
              status: selectedDocumentCandidate.status,
              content: `${isReorganizeCandidate(selectedDocumentCandidate)
                ? `Suggested action: Reorganize "${selectedDocumentCandidate.suggestedArticleTitle || `Knowledge Pack #${selectedDocumentCandidate.suggestedArticleId}`}".`
                : 'Suggested action: Create a new Knowledge Pack.'}\n\n${selectedDocumentCandidate.content}`,
            }}
            actions={selectedDocumentCandidate && (
              <>
                <button type="button" onClick={() => copyToClipboard(selectedDocumentCandidate.content, 'Knowledge candidate copied.')}>Copy</button>
                <button type="button" disabled={selectedDocumentCandidate.status !== 'NEEDS_REVIEW'} onClick={() => openReviewEditor('documentCandidate', selectedDocumentCandidate)}>Edit Before Approve</button>
                <button type="button" disabled={selectedDocumentCandidate.status !== 'NEEDS_REVIEW'} onClick={() => approveSelectedDocumentCandidate(selectedDocumentCandidate)}>{isReorganizeCandidate(selectedDocumentCandidate) ? 'Approve Reorganization' : 'Approve New Pack'}</button>
                <button className="danger" type="button" disabled={selectedDocumentCandidate.status !== 'NEEDS_REVIEW'} onClick={() => rejectSelectedDocumentCandidate(selectedDocumentCandidate)}>Reject</button>
                <button className="danger" type="button" disabled={selectedDocumentCandidate.status === 'CONVERTED'} onClick={() => archiveSelectedDocumentCandidate(selectedDocumentCandidate)}>Archive</button>
                <button className="danger" type="button" disabled={!['REJECTED', 'ARCHIVED'].includes(selectedDocumentCandidate.status)} onClick={() => deleteSelectedDocumentCandidate(selectedDocumentCandidate)}>Delete</button>
              </>
            )}
          />
        </div>
        )}
      </section>
    );
  };

  const renderAssistantPage = () => (
    <section className={`assistantWorkspace ${assistantFullscreen ? 'fullscreen' : ''}`}>
      <aside className="assistantConversations">
        <div className="assistantPanelHeader"><div><span className="panelKicker">WORKSPACE</span><h2>Conversations</h2></div><button type="button" aria-label="New conversation" onClick={() => { setQuestionAnswer(null); setSelectedAiLog(null); setAssistantQuestion(''); setQuestionForm({ question: '', file: null }); }}><span>+</span></button></div>
        <input className="librarySearch" value={aiLogSearch} placeholder="Search conversations..." onChange={(event) => setAiLogSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadAiLogs(1); }} />
        <div className="conversationList">
          {conversationLogs.length ? conversationLogs.map((log) => <button type="button" className={selectedAiLog?.id === log.id ? 'active' : ''} key={log.id} onClick={() => { setSelectedAiLog(log); setQuestionAnswer(log); setAssistantQuestion(log.question || ''); }}><FileText size={15} /><span title={log.question}>{log.question}</span><small>{formatDateTime(log.createdTime)}</small></button>) : <EmptyState text="No conversations yet." />}
        </div>
      </aside>
      <div className="assistantChatPanel">
        <div className="assistantChatHeader"><div><span className="panelKicker">AEGIS AI</span><h2>{selectedAiLog?.question || assistantQuestion ? 'Conversation' : 'How can AegisCloud help you today?'}</h2><p>Search your knowledge, investigate issues, and get grounded AI answers.</p></div><div className="assistantHeaderActions"><button type="button" onClick={() => { setQuestionAnswer(null); setSelectedAiLog(null); setAssistantQuestion(''); setQuestionForm({ question: '', file: null }); }}>New Chat</button><button type="button" aria-label={assistantFullscreen ? 'Exit full screen' : 'Open full screen'} onClick={() => setAssistantFullscreen((value) => !value)}>{assistantFullscreen ? <Minimize2 size={15} /> : <Maximize2 size={15} />}</button></div></div>
        <div className="assistantMessages">
          {!questionAnswer && !selectedAiLog ? <div className="assistantWelcome"><div className="assistantSpark">✦</div><h2>How can AegisCloud help you today?</h2><p>Ask a precise CloudOps question and I will search internal knowledge first, then trusted web evidence when needed.</p><div className="assistantQuickActions"><button type="button" onClick={() => setQuestionForm((current) => ({ ...current, question: 'Troubleshoot a production incident' }))}>Troubleshoot an incident</button><button type="button" onClick={() => setQuestionForm((current) => ({ ...current, question: 'Search internal knowledge for a technical answer' }))}>Search internal knowledge</button><button type="button" onClick={() => setQuestionForm((current) => ({ ...current, question: 'Explain a technical concept clearly' }))}>Explain a technical concept</button><button type="button" onClick={openWebEvidencePage}>Find web evidence</button></div></div> : <div className="assistantThread">
            <div className="assistantUserMessage">{questionAnswer?.question || assistantQuestion || selectedAiLog?.question}</div>
            <article className={`assistantAnswerMessage ${!hasUsableAnswer(questionAnswer || selectedAiLog) ? 'answerUnavailable' : ''}`}><div className="answerMeta"><span className="routeBadge">{answerRouteLabel(questionAnswer || selectedAiLog)}</span><span>{answerEngineLabel(questionAnswer || selectedAiLog)}</span>{hasUsableAnswer(questionAnswer || selectedAiLog) && <button type="button" onClick={() => copyToClipboard(questionAnswer?.answer || selectedAiLog?.answer, 'Answer copied.')}>Copy</button>}</div><KnowledgeContentBlock content={(questionAnswer?.answer || selectedAiLog?.answer) || 'No reliable answer was generated. Review the retrieval trace or retry with a more specific question.'} /><div className="assistantSources"><strong>{hasUsableAnswer(questionAnswer || selectedAiLog) ? 'Grounding' : 'Status'}</strong><span>{hasUsableAnswer(questionAnswer || selectedAiLog) ? (questionAnswer?.sourceTitle || selectedAiLog?.sourceTitle || answerRouteLabel(questionAnswer || selectedAiLog)) : 'No verified source was available'}</span><span>{hasUsableAnswer(questionAnswer || selectedAiLog) ? (questionAnswer?.sourceDomain || selectedAiLog?.sourceDocumentName || 'Source recorded by backend') : 'Web and model fallback were not sufficient'}</span></div></article>
          </div>}
        </div>
        <form className="assistantComposer" onSubmit={submitAiQuestion}><textarea value={questionForm.question} rows={2} placeholder="Ask a question..." aria-label="Ask AegisCloud a question" onChange={(event) => setQuestionForm((current) => ({ ...current, question: event.target.value }))} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form.requestSubmit(); } }} /><div><label className="composerAttach"><Upload size={15} />Attach<input type="file" accept=".txt,.md,.pdf,.docx" onChange={(event) => setQuestionForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))} /></label><button type="button" onClick={openGlobalSearch}><Search size={15} />Search</button><button type="button" onClick={openWebEvidencePage}><Globe2 size={15} />Web Search</button><button className="primaryAction" type="submit">Send <ChevronDown size={15} /></button></div>{actionStatus.text && activePage === 'assistant' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}</form>
      </div>
    </section>
  );

  const renderAiPage = () => (
    <section className="adminPageSurface">
      <div className="adminPageHeader">
        <div>
          <span>AI Activity</span>
          <h2>Reasoning and Question Logs</h2>
          <p>Review real AI usage, source grounding, and answer quality signals.</p>
        </div>
        <div>
          <button type="button" onClick={() => loadAiLogs(1)}>Refresh Logs</button>
          <button type="button" onClick={() => openModal('ask')}>Ask AI</button>
        </div>
      </div>
      <div className="aiActivityMetrics">
        <MetricCard metric={{ key: 'totalQuestions', label: 'Total Questions', value: data.reasoningLayer?.totalQuestions ?? 0, detail: 'Recorded AI requests' }} onClick={() => openAiLogs('questions')} />
        <MetricCard metric={{ key: 'internalRate', label: 'Internal Answer Rate', value: `${data.reasoningLayer?.sourceMix?.find((item) => String(item.source).toUpperCase().includes('INTERNAL'))?.percent ?? 0}%`, detail: 'From internal knowledge' }} onClick={() => openAiLogs('internal')} />
        <MetricCard metric={{ key: 'webRate', label: 'Web-assisted Rate', value: `${data.reasoningLayer?.sourceMix?.find((item) => String(item.source).toUpperCase().includes('WEB'))?.percent ?? 0}%`, detail: 'External evidence assisted' }} onClick={() => openAiLogs('web')} />
        <MetricCard metric={{ key: 'confidence', label: 'Average Confidence', value: data.reasoningLayer?.avgConfidence ?? 0, detail: 'Reported by retrieval pipeline' }} onClick={() => openAiLogs('confidence')} />
      </div>
      <div className="pageSplitGrid aiActivityAnalytics">
        <ReasoningLayer data={data.reasoningLayer} />
        <article className="analyticsCard"><div className="insightHeader"><span>Retrieval & Answer Quality</span><button type="button" onClick={() => openModal('modelStatus')}>Details</button></div><div className="qualityRows"><div><span>Citation coverage</span><strong>{data.reasoningLayer?.citationCoverage ?? 0}%</strong><i><em style={{ width: `${data.reasoningLayer?.citationCoverage ?? 0}%` }} /></i></div><div><span>Hallucination risk</span><strong>{data.reasoningLayer?.hallucinationRisk ?? 0}%</strong><i><em className="dangerFill" style={{ width: `${data.reasoningLayer?.hallucinationRisk ?? 0}%` }} /></i></div><div><span>Sources recorded</span><strong>{(data.reasoningLayer?.sourceMix ?? []).reduce((sum, item) => sum + numberValue(item.count), 0)}</strong></div></div></article>
      </div>
      <div className="activityLogWorkspace">
        <div className="topRecordList">
          <input className="librarySearch" value={aiLogSearch} placeholder="Search AI logs..." onChange={(event) => setAiLogSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadAiLogs(1); }} />
          <div className="libraryFilterBar"><select aria-label="Filter AI logs by answer source" value={aiLogSource} onChange={(event) => { setAiLogSource(event.target.value); loadAiLogs(1, { sourceType: event.target.value }); }}><option value="">All answer sources</option><option value="INTERNAL_RAG">Internal knowledge</option><option value="WEB_SEARCH">Web search</option><option value="AI_ASSISTED_WEB">AI-assisted web</option><option value="AI_ONLY">AI only</option><option value="NONE">No reliable answer</option></select><select aria-label="Filter AI logs by feedback" value={aiLogFeedback} onChange={(event) => { setAiLogFeedback(event.target.value); loadAiLogs(1, { feedback: event.target.value }); }}><option value="">All feedback</option><option value="USEFUL">Useful</option><option value="NOT_USEFUL">Not useful</option></select><button type="button" onClick={() => loadAiLogs(1)}>Apply</button></div>
          {pagedAiLogs.length ? pagedAiLogs.map((log) => (
            <button className="recordButton" type="button" key={log.id} onClick={() => openAiLogDetail(log)}>
              <strong>{log.question}</strong>
              <span>{statusText(log.answerMode || log.sourceType)} · {statusText(log.answerEngine)} · {formatDateTime(log.createdTime)}</span>
              <p>{log.answer}</p>
              <small>{log.llmTotalTokens ? `${formatCount(log.llmTotalTokens)} tokens` : 'Token usage not reported'} · {log.retrievalTrace?.totalLatencyMs ? `${log.retrievalTrace.totalLatencyMs} ms` : 'latency not reported'}</small>
              <em onClick={(event) => { event.stopPropagation(); deleteSelectedAiLog(log); }}>Delete</em>
            </button>
          )) : <EmptyState text="No AI question logs loaded yet." />}
          <ReviewPager page={aiLogPageInfo.page} pageCount={aiLogPageInfo.pageCount} total={aiLogPageInfo.total} onPageChange={loadAiLogs} />
        </div>
      </div>
    </section>
  );

  const renderWebEvidencePage = () => (
    <WebEvidenceWorkspace
      evidence={webEvidence}
      summary={webEvidenceSummary}
      selectedEvidence={selectedEvidence}
      searchValue={webEvidenceSearch}
      actionStatus={activePage === 'webEvidence' ? actionStatus : { type: '', text: '' }}
      onSearchValueChange={setWebEvidenceSearch}
      onSearch={submitWebEvidenceSearch}
      onRefresh={loadWebEvidence}
      onSelect={setSelectedEvidence}
      onEdit={(evidence) => openReviewEditor('webEvidence', evidence)}
      onApprove={approveSelectedEvidence}
      onReject={rejectSelectedEvidence}
      onArchive={archiveSelectedEvidence}
      onDelete={deleteSelectedEvidence}
      pageMeta={webEvidencePageInfo}
      statusFilter={webEvidenceStatus}
      originFilter={webEvidenceOrigin}
      filterKeyword={webEvidenceFilter}
      categoryFilter={webEvidenceCategory}
      onFilterKeywordChange={setWebEvidenceFilter}
      onSearchFilter={() => loadWebEvidence(1)}
      onCategoryFilterChange={(category) => { setWebEvidenceCategory(category); loadWebEvidence(1, webEvidenceStatus, webEvidenceOrigin, { category }); }}
      onStatusFilterChange={(status) => {
        setWebEvidenceStatus(status);
        loadWebEvidence(1, status, webEvidenceOrigin);
      }}
      onOriginFilterChange={(origin) => {
        setWebEvidenceOrigin(origin);
        loadWebEvidence(1, webEvidenceStatus, origin);
      }}
      onPageChange={loadWebEvidence}
    />
  );

  const renderGapsPage = () => (
    <section className="adminPageSurface">
      <div className="adminPageHeader">
        <div>
          <span>Knowledge Gaps</span>
          <h2>Unanswered or Low Confidence Questions</h2>
          <p>Turn repeated misses into durable internal knowledge.</p>
        </div>
        <div>
          <button type="button" onClick={() => loadKnowledgeGaps(1)}>Refresh</button>
        </div>
      </div>
      <KnowledgeGaps
        data={{ gaps: knowledgeGaps }}
        onView={openGapDetail}
        onDelete={deleteSelectedGap}
        keyword={gapSearch}
        onKeywordChange={setGapSearch}
        onSearch={() => loadKnowledgeGaps(1)}
        category={gapCategory}
        onCategoryChange={(value) => { setGapCategory(value); loadKnowledgeGaps(1, { category: value }); }}
        status={gapStatus}
        onStatusChange={(value) => { setGapStatus(value); loadKnowledgeGaps(1, { status: value }); }}
        pageMeta={gapPageInfo}
        onPageChange={loadKnowledgeGaps}
      />
    </section>
  );

  const renderTicketsPage = () => {
    return (
      <section className="adminPageSurface">
        <div className="adminPageHeader">
          <div>
            <span>Tickets</span>
            <h2>Support Escalation Queue</h2>
            <p>Track real open support work created by users or AI escalation.</p>
          </div>
          <div>
            <button type="button" onClick={() => loadTickets(1)}>Refresh Tickets</button>
          </div>
        </div>
        <div className="topRecordList full">
          <div className="libraryFilterBar"><input className="librarySearch" value={ticketSearch} placeholder="Search tickets..." onChange={(event) => setTicketSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') loadTickets(1); }} /><select aria-label="Filter tickets by status" value={ticketListStatus} onChange={(event) => { setTicketListStatus(event.target.value); loadTickets(1, { status: event.target.value }); }}><option value="">All statuses</option><option value="OPEN">Open</option><option value="IN_PROGRESS">In progress</option><option value="RESOLVED">Resolved</option><option value="CLOSED">Closed</option></select><select aria-label="Filter tickets by priority" value={ticketPriority} onChange={(event) => { setTicketPriority(event.target.value); loadTickets(1, { priority: event.target.value }); }}><option value="">All priorities</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select><button type="button" onClick={() => loadTickets(1)}>Apply</button></div>
          {tickets.length ? tickets.map((ticket) => (
            <button className="recordButton" type="button" key={ticket.id || ticket.ticketId || ticket.title} onClick={() => openTicketDetail(ticket)}>
              <strong>{ticket.title}</strong>
              <span>{ticket.priority} · {ticket.status || ticket.owner || 'Open'} · {formatDateTime(ticket.createdTime)}</span>
              <p>{ticket.description || `Owner: ${ticket.owner || 'Unassigned'} · SLA: ${ticket.sla || '-'}`}</p>
            </button>
          )) : <EmptyState text="No open tickets." />}
          <ReviewPager page={ticketPageInfo.page} pageCount={ticketPageInfo.pageCount} total={ticketPageInfo.total} onPageChange={loadTickets} />
        </div>
      </section>
    );
  };

  const renderSettingsPage = () => {
    const model = data.modelSearchControls?.modelProfile ?? {};
    const retrieval = data.modelSearchControls?.retrievalStrategy ?? {};
    const web = data.modelSearchControls?.webSearchSettings ?? {};
    const services = data.serviceHealth ?? [];
    const searchServiceStatus = services.find((service) => service.name === 'Search')?.status;
    const reported = (value) => value === undefined || value === null || value === '' ? 'Not reported by backend' : String(value);

    return (
      <section className="adminPageSurface settingsPage">
        <div className="settingsIntro">
          <div>
            <span>Configuration Snapshot</span>
            <h2>Current system configuration</h2>
            <p>Values below are read from the running backend. This workspace does not expose unpersisted settings controls.</p>
          </div>
          <button type="button" onClick={loadOperationsCenter}><RefreshCw size={15} /> Refresh status</button>
        </div>
        <div className="settingsGrid">
          <section className="settingsPanel">
            <header><Bot size={18} /><div><h3>AI Configuration</h3><p>Model gateway state and usage health</p></div></header>
            <dl>
              <div><dt>Primary model</dt><dd>{reported(model.primaryModel)}</dd></div>
              <div><dt>Gateway</dt><dd>{llmStatus ? (llmStatus.configured ? 'Configured' : 'Not configured') : reported(model.status)}</dd></div>
              <div><dt>Successful calls</dt><dd>{llmStatus ? formatCount(llmStatus.successfulCalls) : 'Not reported'}</dd></div>
              <div><dt>Failed calls</dt><dd>{llmStatus ? formatCount(llmStatus.failedCalls) : 'Not reported'}</dd></div>
            </dl>
            <button className="settingsTextAction" type="button" onClick={() => openModal('modelStatus')}>View gateway details <ChevronDown size={14} /></button>
          </section>
          <section className="settingsPanel">
            <header><Database size={18} /><div><h3>Knowledge Retrieval</h3><p>Active retrieval configuration</p></div></header>
            <dl>
              <div><dt>Document candidates</dt><dd>{reported(retrieval.topKDocuments)}</dd></div>
              <div><dt>Knowledge packs</dt><dd>{reported(retrieval.topKArticles)}</dd></div>
              <div><dt>Reranker</dt><dd>{retrieval.rerankerEnabled === undefined || retrieval.rerankerEnabled === null ? 'Not reported by backend' : retrieval.rerankerEnabled ? 'Enabled' : 'Disabled'}</dd></div>
              <div><dt>Embedding model</dt><dd>{reported(model.embeddingModel)}</dd></div>
            </dl>
          </section>
          <section className="settingsPanel">
            <header><Globe2 size={18} /><div><h3>Web Search</h3><p>Fallback and source policy values</p></div></header>
            <dl>
              <div><dt>Fallback threshold</dt><dd>{reported(web.webFallbackThreshold)}</dd></div>
              <div><dt>Trusted domains</dt><dd>{reported(web.trustedDomains)}</dd></div>
              <div><dt>Maximum results</dt><dd>{reported(web.maxWebResults)}</dd></div>
              <div><dt>Search service</dt><dd>{searchServiceStatus ? statusText(searchServiceStatus) : 'Not reported by backend'}</dd></div>
            </dl>
            <button className="settingsTextAction" type="button" onClick={() => handleNavAction('webEvidence')}>Open Web Evidence <ChevronDown size={14} /></button>
          </section>
          <section className="settingsPanel">
            <header><ShieldCheck size={18} /><div><h3>Security & Access</h3><p>Current administrator session</p></div></header>
            <dl>
              <div><dt>Authentication</dt><dd>OAuth</dd></div>
              <div><dt>Role</dt><dd>{user.role || 'Administrator'}</dd></div>
              <div><dt>Session identity</dt><dd>{user.email || user.username || 'Current account'}</dd></div>
              <div><dt>Secret handling</dt><dd>Credentials remain server-side</dd></div>
            </dl>
            <button className="settingsTextAction" type="button" onClick={openProfile}>View account profile <ChevronDown size={14} /></button>
          </section>
          <section className="settingsPanel settingsServices">
            <header><Activity size={18} /><div><h3>System Health</h3><p>Live state reported by the operations API</p></div></header>
            {services.length ? <div className="settingsServiceList">{services.map((service) => (
              <div key={service.name}>
                <span className={`serviceDot ${statusClass(service.status)}`} />
                <strong>{service.name}</strong>
                <span>{statusText(service.status)}</span>
              </div>
            ))}</div> : <EmptyState text="Service health is unavailable until the backend responds." />}
          </section>
        </div>
      </section>
    );
  };

  const renderActivePage = () => {
    if (activePage === 'command' && loadState !== 'live') {
      return (
        <section className="adminUnavailableState" aria-live="polite">
          {loadState === 'loading' ? <RefreshCw size={22} className="loadingIcon" /> : <Server size={22} />}
          <div>
            <h2>{loadState === 'loading' ? 'Loading live operations data' : 'Dashboard data is unavailable'}</h2>
            <p>{loadState === 'loading'
              ? 'Metrics and activity will appear after the operations API responds.'
              : 'No sample metrics are shown. Check the backend connection, then refresh to load real data.'}</p>
          </div>
          {loadState === 'fallback' && <button type="button" onClick={loadOperationsCenter}>Retry connection</button>}
        </section>
      );
    }
    if (activePage === 'knowledgePackDetail' || activePage === 'articleDetail') return renderArticleDetailPage();
    if (activePage === 'documentDetail') return renderDocumentDetailPage();
    if (activePage === 'knowledge') return renderKnowledgePage();
    if (activePage === 'assistant') return renderAssistantPage();
    if (activePage === 'documents') return renderDocumentPage();
    if (activePage === 'documentLearning') return renderDocumentLearningPage();
    if (activePage === 'webEvidence') return renderWebEvidencePage();
    if (activePage === 'ai') return renderAiPage();
    if (activePage === 'gaps') return renderGapsPage();
    if (activePage === 'tickets') return renderTicketsPage();
    if (activePage === 'users') return <AdminUserManagement />;
    if (activePage === 'settings') return renderSettingsPage();
    return renderCommandCenterPage();
  };

  const renderModal = () => {
    if (activeModal === 'uploadArticle') {
      return (
        <AdminModal title="Upload Knowledge Article" onClose={closeModal}>
          <form className="adminActionForm" onSubmit={submitArticleUpload}>
            <Field label="Article file">
              <input type="file" accept=".txt,.md,.pdf,.docx" onChange={(event) => setArticleForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))} />
            </Field>
            <Field label="Title">
              <input value={articleForm.title} placeholder="Use filename if left blank" onChange={(event) => setArticleForm((current) => ({ ...current, title: event.target.value }))} />
            </Field>
            <Field label="Category">
              <input value={articleForm.category} onChange={(event) => setArticleForm((current) => ({ ...current, category: event.target.value }))} />
            </Field>
            <button className="adminSubmitButton" type="submit">Save Article</button>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'uploadDocument') {
      return (
        <AdminModal title="Upload Runbook Document" onClose={closeModal}>
          <form className="adminActionForm" onSubmit={submitDocumentUpload}>
            <Field label="Document file">
              <input type="file" accept=".txt,.md,.pdf,.docx" onChange={(event) => setDocumentForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))} />
            </Field>
            <Field label="Category">
              <input value={documentForm.category} onChange={(event) => setDocumentForm((current) => ({ ...current, category: event.target.value }))} />
            </Field>
            <button className="adminSubmitButton" type="submit">Upload Document</button>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'ask') {
      return (
        <AdminModal title="Ask AI With Optional File" onClose={closeModal}>
          <form className="adminActionForm" onSubmit={submitAiQuestion}>
            <Field label="Question">
              <textarea
                value={questionForm.question}
                rows={5}
                placeholder="Ask about an uploaded file, internal knowledge, or a CloudOps issue"
                onChange={(event) => setQuestionForm((current) => ({ ...current, question: event.target.value }))}
              />
            </Field>
            <Field label="Attach file optional">
              <input
                type="file"
                accept=".txt,.md,.pdf,.docx"
                onChange={(event) => setQuestionForm((current) => ({ ...current, file: event.target.files?.[0] ?? null }))}
              />
            </Field>
            <button className="adminSubmitButton" type="submit">Ask AI</button>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            {questionAnswer && (
              <div className="detailReadableBlock aiAnswerBlock">
                <div className="answerHeaderRow">
                  <strong>{answerRouteLabel(questionAnswer)} · {answerEngineLabel(questionAnswer)}</strong>
                  <div>
                    <button type="button" onClick={() => copyToClipboard(questionAnswer.answer, 'AI answer copied.')}>Copy</button>
                    <button type="button" onClick={() => setAnswerGroundingOpen((value) => !value)}>{answerGroundingOpen ? 'Hide Grounding' : 'Show Grounding'}</button>
                  </div>
                </div>
                <KnowledgeContentBlock content={questionAnswer.answer} />
                {answerGroundingOpen && (
                  <InfoGroup title="Grounding" rows={[
                    ['Source Type', statusText(questionAnswer.sourceType)],
                    ['Answer Engine', questionAnswer.answerEngine === 'LLM' ? 'LLM synthesized' : 'Deterministic grounded answer'],
                    ['Document', questionAnswer.sourceDocumentName],
                    ['Article', questionAnswer.sourceTitle],
                    ['Web', questionAnswer.sourceDomain],
                  ]} />
                )}
              </div>
            )}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'aiLogDetail' && selectedAiLog) {
      return (
        <AdminModal title="AI Answer Detail" onClose={closeModal}>
          <div className="detailWorkspace">
            <div className="detailHero">
              <span>{statusText(selectedAiLog.sourceType)} Source</span>
              <h3>{selectedAiLog.question}</h3>
              <p>{formatDateTime(selectedAiLog.createdTime)}</p>
            </div>
            <InfoGroup title="Grounding" rows={[
              ['Source Type', statusText(selectedAiLog.sourceType)],
              ['Answer Mode', selectedAiLog.answerMode || 'Not recorded'],
              ['Answer Engine', selectedAiLog.answerEngine || 'Not recorded'],
              ['Article', selectedAiLog.sourceTitle],
              ['Document', selectedAiLog.sourceDocumentName],
              ['Chunk', selectedAiLog.sourceChunkIndex],
              ['Created Ticket', selectedAiLog.createdTicketId],
            ]} />
            <InfoGroup title="Model Usage" rows={[
              ['Provider', selectedAiLog.llmProvider],
              ['Model', selectedAiLog.llmModel],
              ['Tokens', selectedAiLog.llmTotalTokens == null ? null : `${selectedAiLog.llmInputTokens ?? 0} in / ${selectedAiLog.llmOutputTokens ?? 0} out`],
              ['Estimated Cost', selectedAiLog.llmEstimatedCost == null ? null : `${selectedAiLog.llmCurrency || 'USD'} ${selectedAiLog.llmEstimatedCost}`],
              ['Latency', selectedAiLog.llmLatencyMs == null ? null : `${selectedAiLog.llmLatencyMs} ms`],
              ['Request Status', selectedAiLog.llmRequestStatus],
              ['Error Category', selectedAiLog.llmErrorCategory],
            ]} />
            <div className="detailReadableBlock retrievalTraceBlock">
              <h3>Retrieval Trace</h3>
              {selectedAiLog.retrievalTrace?.stages?.length ? (
                <div className="retrievalTraceList">
                  {selectedAiLog.retrievalTrace.stages.map((stage) => (
                    <div className="retrievalTraceRow" key={stage.stage}>
                      <strong>{stage.stage}</strong>
                      <span className={`statusPill ${String(stage.status || 'NOT_ATTEMPTED').toLowerCase()}`}>{stage.status || 'NOT_ATTEMPTED'}</span>
                      <small>{stage.durationMs == null ? '—' : `${stage.durationMs} ms`}</small>
                      <p>{stage.resultSummary || stage.errorCategory || 'No stage detail recorded.'}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p>No structured trace was recorded for this historical question.</p>
              )}
              {selectedAiLog.retrievalTrace?.failureReason && <p className="traceFailure">Failure reason: {selectedAiLog.retrievalTrace.failureReason}</p>}
            </div>
            <div className="detailReadableBlock">
              <h3>Answer</h3>
              <KnowledgeContentBlock content={selectedAiLog.answer} />
            </div>
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="button" onClick={() => copyToClipboard(selectedAiLog.answer, 'AI answer copied.')}>Copy Answer</button>
              {selectedAiLog.sourceType === 'NONE' && <button className="secondaryAdminButton" type="button" onClick={async () => { closeModal(); setActivePage('gaps'); await loadKnowledgeGaps(1); }}>Open Knowledge Gaps</button>}
              {selectedAiLog.sourceType === 'WEB' && <button className="secondaryAdminButton" type="button" onClick={openWebEvidencePage}>Open Web Evidence</button>}
              <button className="danger" type="button" onClick={() => deleteSelectedAiLog(selectedAiLog)}>Delete Log</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'gapDetail' && selectedGap) {
      return (
        <AdminModal title="Review Knowledge Gap" onClose={closeModal}>
          <form className="adminActionForm wide" onSubmit={submitGapArticle}>
            <div className="detailHero">
              <span>{selectedGap.priority || 'Review'} Priority</span>
              <h3>{selectedGap.question}</h3>
              <p>{selectedGap.category || 'General'} · Frequency {selectedGap.frequency ?? 1}</p>
            </div>
            <Field label="Article title">
              <input value={gapArticleDraft.title} onChange={(event) => setGapArticleDraft((current) => ({ ...current, title: event.target.value }))} />
            </Field>
            <Field label="Category">
              <input value={gapArticleDraft.category} onChange={(event) => setGapArticleDraft((current) => ({ ...current, category: event.target.value }))} />
            </Field>
            <Field label="Reviewed article content">
              <textarea value={gapArticleDraft.content} rows={16} onChange={(event) => setGapArticleDraft((current) => ({ ...current, content: event.target.value }))} />
            </Field>
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="submit">Publish Article</button>
              <button className="secondaryAdminButton" type="button" onClick={() => copyToClipboard(gapArticleDraft.content, 'Draft article copied.')}>Copy Draft</button>
              <button className="secondaryAdminButton dangerText" type="button" onClick={() => deleteSelectedGap(selectedGap)}>Delete Gap</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'ticketDetail' && selectedTicket) {
      return (
        <AdminModal title="Ticket Detail" onClose={closeModal}>
          <div className="detailWorkspace">
            <div className="detailHero">
              <span>{selectedTicket.priority || 'MEDIUM'} Priority</span>
              <h3>{selectedTicket.title}</h3>
              <p>{statusText(selectedTicket.status)} · Created {formatDateTime(selectedTicket.createdTime)}</p>
            </div>
            <InfoGroup title="Ownership" rows={[
              ['Ticket ID', selectedTicket.id],
              ['Created By', selectedTicket.createdBy],
              ['Assigned To', selectedTicket.assignedTo],
              ['Updated', formatDateTime(selectedTicket.updatedTime)],
            ]} />
            <div className="detailReadableBlock">
              <h3>Description</h3>
              <KnowledgeContentBlock content={selectedTicket.description || 'No description.'} />
            </div>
            <form className="inlineStatusForm" onSubmit={submitTicketStatus}>
              <label>
                <span>Status</span>
                <select value={ticketStatus} onChange={(event) => setTicketStatus(event.target.value)}>
                  <option value="OPEN">Open</option>
                  <option value="IN_PROGRESS">In Progress</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="CLOSED">Closed</option>
                </select>
              </label>
              <button className="adminSubmitButton" type="submit">Save Status</button>
            </form>
            <div className="formButtonRow">
              <button className="secondaryAdminButton" type="button" onClick={resolveSelectedTicket}>Resolve</button>
              <button className="secondaryAdminButton dangerText" type="button" onClick={closeSelectedTicket}>Close</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'globalSearch') {
      const totalResults = (globalSearchResults.articles?.length ?? 0) + (globalSearchResults.chunks?.length ?? 0);
      return (
        <AdminModal title="Search Stored Knowledge" onClose={closeModal}>
          <div className="globalSearchModal">
            <form className="globalSearchForm" onSubmit={submitGlobalSearch}>
              <input value={globalSearch} placeholder="Search articles and document chunks" onChange={(event) => setGlobalSearch(event.target.value)} autoFocus />
              <button type="submit">Search</button>
            </form>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="globalSearchSummary">{totalResults} results from stored internal knowledge</div>
            <div className="globalSearchResults">
              <section>
                <h3>Articles</h3>
                {globalSearchResults.articles?.length ? globalSearchResults.articles.map((article) => (
                  <button type="button" key={article.id} onClick={() => openSearchArticle(article)}>
                    <strong>{article.title}</strong>
                    <span>{article.category || 'Uncategorized'}</span>
                    <p>{article.content}</p>
                  </button>
                )) : <EmptyState text="No matching articles." />}
              </section>
              <section>
                <h3>Document Chunks</h3>
                {globalSearchResults.chunks?.length ? globalSearchResults.chunks.map((chunk) => (
                  <button type="button" key={chunk.chunkId} onClick={() => openSearchChunk(chunk)}>
                    <strong>{chunk.originalFilename} · Chunk {chunk.chunkIndex}</strong>
                    <span>{chunk.category || 'Uncategorized'}</span>
                    <p>{chunk.content}</p>
                  </button>
                )) : <EmptyState text="No matching document chunks." />}
              </section>
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'editArticle') {
      return (
        <AdminModal title="Edit Knowledge Article" onClose={closeModal}>
          <form className="adminActionForm wide" onSubmit={submitArticleEdit}>
            <Field label="Title">
              <input value={articleEditor.title} onChange={(event) => setArticleEditor((current) => ({ ...current, title: event.target.value }))} />
            </Field>
            <Field label="Category">
              <input value={articleEditor.category} onChange={(event) => setArticleEditor((current) => ({ ...current, category: event.target.value }))} />
            </Field>
            <Field label="Content">
              <textarea value={articleEditor.content} rows={16} onChange={(event) => setArticleEditor((current) => ({ ...current, content: event.target.value }))} />
            </Field>
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="submit">Save Changes</button>
              <button className="secondaryAdminButton" type="button" onClick={closeModal}>Cancel</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'editChunk') {
      return (
        <AdminModal title="Edit Retrieval Chunk" onClose={closeModal}>
          <form className="adminActionForm wide" onSubmit={submitChunkEdit}>
            <Field label="Chunk content">
              <textarea value={chunkEditor.content} rows={16} onChange={(event) => setChunkEditor((current) => ({ ...current, content: event.target.value }))} />
            </Field>
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="submit">Save Chunk</button>
              <button className="secondaryAdminButton" type="button" onClick={closeModal}>Cancel</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'editReviewContent') {
      return (
        <AdminModal title="Edit Review Content Before Approval" onClose={closeModal}>
          <form className="adminActionForm wide" onSubmit={submitReviewEdit}>
            <Field label="Title">
              <input value={reviewEditor.title} onChange={(event) => setReviewEditor((current) => ({ ...current, title: event.target.value }))} />
            </Field>
            <Field label="Category">
              <input value={reviewEditor.category} onChange={(event) => setReviewEditor((current) => ({ ...current, category: event.target.value }))} />
            </Field>
            <Field label="Reviewed knowledge content">
              <textarea value={reviewEditor.content} rows={16} onChange={(event) => setReviewEditor((current) => ({ ...current, content: event.target.value }))} />
            </Field>
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="submit">Save Review Content</button>
              <button className="secondaryAdminButton" type="button" onClick={closeModal}>Cancel</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'retrievalIndex') {
      return (
        <AdminModal title="Retrieval Index" onClose={closeModal}>
          <div className="knowledgeLibrary retrievalIndexWorkspace">
            <div className="libraryList">
              <div className="libraryToolbar">
                <div>
                  <strong>{chunkPageInfo.total} Active Retrieval Units</strong>
                  <span>Semantic passages used for search; these are not separate knowledge articles.</span>
                </div>
                <div className="formButtonRow">
                  <button type="button" onClick={() => loadAdminChunks(1)}>Refresh</button>
                  <button type="button" onClick={rebuildLibraryQuality}>Rebuild Index</button>
                </div>
              </div>
              <input
                className="librarySearch"
                value={chunkSearch}
                placeholder="Search retrieval units..."
                onChange={(event) => setChunkSearch(event.target.value)}
                onKeyDown={(event) => { if (event.key === 'Enter') loadAdminChunks(1); }}
                autoFocus
              />
              {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
              {pagedAdminChunks.length ? pagedAdminChunks.map((chunk) => (
                <button
                  className={selectedAdminChunk?.id === chunk.id ? 'active' : ''}
                  type="button"
                  key={chunk.id}
                  onClick={() => setSelectedAdminChunk(chunk)}
                >
                  <strong>{chunk.originalFilename || `Article ${chunk.articleId || '-'}`} Â· Chunk {chunk.chunkIndex ?? '-'}</strong>
                  <span>{chunk.category || 'Uncategorized'} Â· {statusText(chunk.status)}</span>
                  <p>{chunk.content}</p>
                </button>
              )) : <EmptyState text="No active retrieval units match this search." />}
              <LibraryPager page={chunkPageInfo.page} pageCount={chunkPageInfo.pageCount} total={chunkPageInfo.total} onPageChange={loadAdminChunks} />
            </div>
            <KnowledgeReader
              chunks={selectedAdminChunk ? [selectedAdminChunk] : []}
              actions={selectedAdminChunk && (
                <>
                  <button type="button" onClick={() => openChunkEditor(selectedAdminChunk)}>Edit</button>
                  <button type="button" onClick={() => copyToClipboard(selectedAdminChunk.content, 'Retrieval unit copied.')}>Copy</button>
                  <button className="danger" type="button" onClick={archiveSelectedChunk}>Archive</button>
                </>
              )}
            />
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'coverageDetail') {
      const categories = data.knowledgeCore?.categories ?? [];
      return (
        <AdminModal title="Knowledge Coverage Detail" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">Coverage is calculated from real published articles and active uploaded documents across the core CloudOps domains.</p>
            <div className="coverageDetailGrid">
              {categories.map((category) => (
                <article key={category.category}>
                  <strong>{category.category}</strong>
                  <span>{category.articleCount ?? 0} articles · {category.documentCount ?? 0} documents</span>
                  <div><i style={{ width: `${category.coveragePercent ?? 0}%` }} /></div>
                  <b>{category.coveragePercent ?? 0}%</b>
                </article>
              ))}
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'aiLogs') {
      return (
        <AdminModal title={metricContext === 'confidence' ? 'Answer Confidence Sources' : 'Questions Answered In 7 Days'} onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">
              {metricContext === 'confidence'
                ? 'Confidence is derived from the ratio of answers grounded in ARTICLE or DOCUMENT sources.'
                : 'This list is loaded from the real AI question log table.'}
            </p>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="topRecordList">
              {aiLogs.length ? aiLogs.map((log) => (
                <button className="recordButton" type="button" key={log.id} onClick={() => openAiLogDetail(log)}>
                  <strong>{log.question}</strong>
                  <span>{statusText(log.sourceType)} · {formatDateTime(log.createdTime)}</span>
                  <p>{log.answer}</p>
                  <em onClick={(event) => { event.stopPropagation(); deleteSelectedAiLog(log); }}>Delete</em>
                </button>
              )) : <EmptyState text="No matching AI records yet." />}
              <ReviewPager page={aiLogPageInfo.page} pageCount={aiLogPageInfo.pageCount} total={aiLogPageInfo.total} onPageChange={loadAiLogs} />
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'ticketList') {
      return (
        <AdminModal title="Open Ticket Queue" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">Open tickets are loaded from the real ticket queue and update as tickets are created or resolved.</p>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="topRecordList">
              {tickets.length ? tickets.map((ticket) => (
                <button className="recordButton" type="button" key={ticket.id} onClick={() => openTicketDetail(ticket)}>
                  <strong>{ticket.title}</strong>
                  <span>{ticket.priority} · {ticket.status} · {formatDateTime(ticket.createdTime)}</span>
                  <p>{ticket.description}</p>
                </button>
              )) : <EmptyState text="No open tickets." />}
              <ReviewPager page={ticketPageInfo.page} pageCount={ticketPageInfo.pageCount} total={ticketPageInfo.total} onPageChange={loadTickets} />
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'notifications') {
      const openTicketRows = notificationSnapshot.tickets;
      const unreadGapRows = notificationSnapshot.gaps;
      const unreadEvidenceRows = notificationSnapshot.evidence;
      const unreadDocumentCandidateRows = notificationSnapshot.documentCandidates;
      return (
        <AdminModal title="Admin Notifications" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">Notifications show new unread file insights, web evidence, knowledge gaps, and open support tickets that need administrator action.</p>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="notificationGroups">
              <section>
                <h3>File Insights</h3>
                {unreadDocumentCandidateRows.length ? unreadDocumentCandidateRows.map((item) => (
                  <button type="button" key={item.id} onClick={() => { setSelectedDocumentCandidate(item); setActivePage('documentLearning'); closeModal(); }}>
                    <strong>{item.title}</strong>
                    <span>{item.category || 'General'} · {statusText(item.status)}</span>
                  </button>
                )) : <EmptyState text="No file insights awaiting review." />}
              </section>
              <section>
                <h3>Knowledge Gaps</h3>
                {unreadGapRows.length ? unreadGapRows.map((gap) => (
                  <button type="button" key={gap.logId} onClick={() => openGapDetail(gap)}>
                    <strong>{gap.question}</strong>
                    <span>{formatDateTime(gap.createdTime)}</span>
                  </button>
                )) : <EmptyState text="No knowledge gaps." />}
              </section>
              <section>
                <h3>Open Tickets</h3>
                {openTicketRows.length ? openTicketRows.map((ticket) => (
                  <button type="button" key={ticket.id} onClick={() => openTicketDetail(ticket)}>
                    <strong>{ticket.title}</strong>
                    <span>{ticket.priority} · {ticket.status}</span>
                  </button>
                )) : <EmptyState text="No open tickets." />}
              </section>
              <section>
                <h3>Web Evidence</h3>
                {unreadEvidenceRows.length ? unreadEvidenceRows.map((item) => (
                  <button type="button" key={item.id} onClick={() => { setSelectedEvidence(item); setActivePage('webEvidence'); closeModal(); }}>
                    <strong>{item.title || item.question}</strong>
                    <span>{item.domain || 'Unknown domain'} · {statusText(item.status)}</span>
                  </button>
                )) : <EmptyState text="No web evidence awaiting review." />}
              </section>
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'profile') {
      const account = profile || user || {};
      return (
        <AdminModal title="Platform Admin Profile" onClose={closeModal}>
          <div className="profilePanel">
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="profileHero">
              <div><UserRound size={28} /></div>
              <section>
                <strong>{account.displayName || account.username || 'Admin'}</strong>
                <span>{account.email || 'No email on record'}</span>
              </section>
            </div>
            <InfoGroup title="Account" rows={[
              ['User ID', account.id],
              ['Username', account.username],
              ['Role', account.role],
              ['Status', account.status === 1 ? 'Active' : account.status],
              ['Created', formatDateTime(account.createdTime)],
            ]} />
            <div className="formButtonRow">
              <button className="adminSubmitButton" type="button" onClick={loadOperationsCenter}>Refresh Dashboard</button>
              <button className="secondaryAdminButton dangerText" type="button" onClick={handleLogout}>Sign Out</button>
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'environmentInfo') {
      return (
        <AdminModal title="Environment Status" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">This panel is computed from the current backend response and active knowledge storage state.</p>
            <div className="environmentGrid">
              <InfoGroup title="Environment" rows={[
                ['Name', data.environment?.name],
                ['Status', statusText(data.environment?.status)],
                ['Data Mode', loadState === 'live' ? 'Live backend data' : 'Local fallback structure'],
              ]} />
              <InfoGroup title="Storage Summary" rows={[
                ['Articles', data.metrics?.find((metric) => metric.key === 'totalKnowledgeArticles')?.value],
                ['Documents', data.metrics?.find((metric) => metric.key === 'runbookDocuments')?.value],
                ['Open Tickets', data.metrics?.find((metric) => metric.key === 'openTickets')?.value],
              ]} />
            </div>
            <div className="serviceHealthBar detail">
              {(data.serviceHealth ?? []).map((service) => (
                <button className={statusClass(service.status)} type="button" key={service.name}>
                  <span />
                  <strong>{service.name}</strong>
                  <b>{statusText(service.status)}</b>
                </button>
              ))}
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'modelStatus') {
      const profile = data.modelSearchControls?.modelProfile ?? {};
      const retrieval = data.modelSearchControls?.retrievalStrategy ?? {};
      const web = data.modelSearchControls?.webSearchSettings ?? {};
      return (
        <AdminModal title="LLM Gateway Status" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">This is the real current model configuration state returned by the backend. Not configured means no model gateway has been connected yet.</p>
            <div className="environmentGrid">
              <InfoGroup title="Model Profile" rows={[
                ['Primary Model', profile.primaryModel],
                ['Embedding Model', profile.embeddingModel],
                ['Reranker Model', profile.rerankerModel],
                ['Status', statusText(profile.status)],
                ['Configured', llmStatus?.configured ? 'Yes' : 'No'],
                ['Last Call', llmStatus?.lastCallType || 'No call recorded'],
                ['Successful Calls', llmStatus?.successfulCalls ?? 0],
                ['Failed Calls', llmStatus?.failedCalls ?? 0],
              ]} />
              <InfoGroup title="Retrieval" rows={[
                ['Top-K Documents', retrieval.topKDocuments],
                ['Top-K Articles', retrieval.topKArticles],
                ['Reranker Enabled', retrieval.rerankerEnabled ? 'On' : 'Off'],
                ['Web Threshold', web.webFallbackThreshold],
              ]} />
            </div>
            {llmStatus?.lastFailureReason && (
              <div className="adminActionStatus error">
                Last model failure: {llmStatus.lastFailureReason}
              </div>
            )}
          </div>
        </AdminModal>
      );
    }

    const capabilityMessages = {
      trustedSourcesUnavailable: ['Trusted Sources', 'Trusted domain management needs a dedicated trusted_sources table and review workflow. No fake trusted domains are created.'],
      documentationUnavailable: ['System Documentation', 'Documentation pages are not implemented yet. This action is reserved for the final help center.'],
    };

    if (capabilityMessages[activeModal]) {
      const [title, description] = capabilityMessages[activeModal];
      return (
        <AdminModal title={title} onClose={closeModal}>
          <CapabilityNotice title="Capability not configured" description={description} />
        </AdminModal>
      );
    }

    return null;
  };

  return (
    <div className={`adminShell ${sidebarCollapsed ? 'sidebarCollapsed' : ''}`}>
      {renderModal()}
      {mobileSidebarOpen && <button className="mobileSidebarScrim" type="button" aria-label="Close navigation" onClick={() => setMobileSidebarOpen(false)} />}
      <aside className={`adminSidebar ${mobileSidebarOpen ? 'mobileOpen' : ''}`}>
        <div className="adminLogo">
          <Cloud size={38} />
          <div>
            <strong>AegisCloud</strong>
            <span>CloudOps AI Knowledge Platform</span>
          </div>
        </div>
        <nav className="adminNav">
          <span className="navGroupLabel">Overview</span>
          {[["command", "Dashboard", Home], ["assistant", "AI Assistant", Bot]].map(([id, label, Icon]) => (
            <button className={activePage === id ? 'active' : ''} type="button" key={id} title={sidebarCollapsed ? label : undefined} aria-current={activePage === id ? 'page' : undefined} onClick={() => handleNavAction(id)}><Icon size={18} /><span>{label}</span></button>
          ))}
          <button className="navGroupToggle" type="button" onClick={() => setKnowledgeNavOpen((value) => !value)}><BookOpen size={18} /><span>Knowledge</span><ChevronDown size={14} className={knowledgeNavOpen ? '' : 'collapsed'} /></button>
          {knowledgeNavOpen && <div className="navSubgroup">
            {[["knowledge", "Knowledge Packs", BookOpen], ["documents", "Documents", FileText], ["documentLearning", "Review & Approvals", Target], ["webEvidence", "Web Evidence", Globe2], ["gaps", "Knowledge Gaps", AlertTriangle]].map(([id, label, Icon]) => (
              <button className={activePage === id ? 'active' : ''} type="button" key={id} title={sidebarCollapsed ? label : undefined} aria-current={activePage === id ? 'page' : undefined} onClick={() => handleNavAction(id)}><Icon size={15} /><span>{label}</span></button>
            ))}
          </div>}
          <span className="navGroupLabel">Operations</span>
          {[["ai", "AI Activity", Bot], ["tickets", "Support Tickets", GitBranch]].map(([id, label, Icon]) => (
            <button className={activePage === id ? 'active' : ''} type="button" key={id} title={sidebarCollapsed ? label : undefined} aria-current={activePage === id ? 'page' : undefined} onClick={() => handleNavAction(id)}><Icon size={18} /><span>{label}</span></button>
          ))}
          <span className="navGroupLabel">Administration</span>
          <button className={`navGroupToggle ${['settings', 'users'].includes(activePage) ? 'activeParent' : ''}`} type="button" onClick={() => { navigateAdmin('/admin/settings'); setSettingsNavOpen(true); }}><Settings size={18} /><span>Settings</span><ChevronDown size={14} className={settingsNavOpen ? '' : 'collapsed'} /></button>
          {settingsNavOpen && <div className="navSubgroup">
            {[["users", "User Management", UsersRound]].map(([id, label, Icon]) => (
              <button className={activePage === id ? 'active' : ''} type="button" key={id} title={sidebarCollapsed ? label : undefined} aria-current={activePage === id ? 'page' : undefined} onClick={() => handleNavAction(id)}><Icon size={15} /><span>{label}</span></button>
            ))}
          </div>}
        </nav>
        <button className="lastUpdated" type="button" onClick={loadOperationsCenter}>
          <span>System status</span>
          <strong>{loadState === 'live' ? ((data.serviceHealth ?? []).length && (data.serviceHealth ?? []).every((service) => ['HEALTHY', 'ACTIVE'].includes(String(service.status).toUpperCase())) ? 'All systems healthy' : 'Attention required') : loadState === 'loading' ? 'Checking services…' : 'Backend unavailable'}</strong>
          <RefreshCw size={16} />
        </button>
        <button className="collapseButton" type="button" onClick={() => setSidebarCollapsed((value) => !value)}><PanelLeftClose size={18} />Collapse</button>
      </aside>

      <main className="adminMain">
        <header className="adminTopSection">
          <div className="adminTopbar">
            <button className="mobileMenuButton" type="button" aria-label="Open navigation" onClick={() => setMobileSidebarOpen(true)}><Menu size={20} /></button>
            <div className="adminTitleBlock">
              <span className="adminEyebrow">AegisCloud Operations</span>
              <h1>{pageHeadings[activePage]?.[0] ?? 'Dashboard'}</h1>
              <p>{pageHeadings[activePage]?.[1] ?? 'System overview and key operational metrics'}</p>
            </div>
            <div className="topbarControls">
              <button className={`environmentPill ${statusClass(data.environment?.status)}`} type="button" onClick={() => openModal('environmentInfo')}>
                <span />{data.environment?.name ?? 'Unknown'}<ChevronDown size={14} />
              </button>
              <form className="adminSearch" onSubmit={submitGlobalSearch}>
                <input value={globalSearch} placeholder="Search knowledge, documents..." aria-label="Search knowledge and documents" onChange={(event) => setGlobalSearch(event.target.value)} />
                <button type="submit" aria-label="Search stored knowledge"><Search size={18} /></button>
                <kbd>Ctrl K</kbd>
              </form>
              <button className={`notificationButton ${unreadNotificationCount > 0 ? 'hasUnread' : ''}`} type="button" onClick={openNotifications}>
                <Bell size={19} />
                {unreadNotificationCount > 0 && <i>{unreadNotificationCount}</i>}
              </button>
              <button className="adminProfile" type="button" onClick={openProfile}>
                <div><UserRound size={18} /></div>
                <p><strong>{user.displayName || user.username || 'Admin'}</strong><span>Platform Admin</span></p>
                <ChevronDown size={14} />
              </button>
            </div>
          </div>

          <div className="serviceHealthBar" aria-label="Service health">
            {(data.serviceHealth ?? []).map((service) => (
              <button className={statusClass(service.status)} type="button" key={service.name} onClick={() => service.name === 'LLM Gateway' ? openModal('modelStatus') : openModal('environmentInfo')}>
                <span />
                <strong>{service.name}</strong>
                <b>{statusText(service.status)}</b>
              </button>
            ))}
          </div>
        </header>

        {loadState === 'fallback' && (
          <div className="adminBanner">Backend data is unavailable. Showing empty structure only; no data is fabricated.</div>
        )}

        {renderActivePage()}

        <footer className="adminFooter">
          <span>i</span>
          <p><b>Tip:</b> {data.adminTip}</p>
          <button type="button" onClick={() => openModal('documentationUnavailable')}>System Documentation</button>
        </footer>
      </main>
    </div>
  );
}

export default AdminOperationsCenter;
