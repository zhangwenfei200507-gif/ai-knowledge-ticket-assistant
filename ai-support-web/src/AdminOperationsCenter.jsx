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
  LifeBuoy,
  LockKeyhole,
  Megaphone,
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
} from 'lucide-react';
import {
  approveWebEvidence,
  archiveKnowledgeArticle,
  archiveKnowledgeDocument,
  askAiQuestion,
  getAiQuestionLogs,
  getCurrentUser,
  getDocumentChunks,
  getKnowledgeGaps,
  getKnowledgeArticles,
  getKnowledgeDocuments,
  getOperationsCenter,
  getTickets,
  getWebEvidence,
  logoutCurrentUser,
  rejectWebEvidence,
  searchDocumentChunks,
  searchKnowledgeArticles,
  searchWebEvidence,
  updateKnowledgeArticle,
  uploadKnowledgeArticle,
  uploadKnowledgeDocument,
} from './api/admin';

const fallbackData = {
  title: 'CloudOps AI Knowledge Operations Center',
  subtitle: 'Operate knowledge. Improve answers. Deliver reliable support.',
  environment: { name: 'Production', status: 'UNKNOWN' },
  serviceHealth: [
    { name: 'API', status: 'UNKNOWN' },
    { name: 'Search', status: 'UNKNOWN' },
    { name: 'LLM Gateway', status: 'NOT_CONFIGURED' },
    { name: 'Storage', status: 'UNKNOWN' },
  ],
  metrics: [
    { key: 'totalKnowledgeArticles', label: 'Total Knowledge Articles', value: '0', trend: '+ 0 this week', trendType: 'neutral', detail: 'Published internal articles', icon: 'article' },
    { key: 'runbookDocuments', label: 'Runbook Documents', value: '0', trend: '+ 0 this week', trendType: 'neutral', detail: 'Active uploaded documents', icon: 'book' },
    { key: 'knowledgeCoverage', label: 'Knowledge Coverage', value: '0%', trend: '0% of core domains covered', trendType: 'warning', detail: 'Based on article and document categories', icon: 'coverage' },
    { key: 'questionsAnswered7d', label: 'Questions Answered (7D)', value: '0', trend: '+ 0 last 7 days', trendType: 'neutral', detail: 'Recorded AI answers in the last 7 days', icon: 'question' },
    { key: 'avgAnswerConfidence', label: 'Avg Answer Confidence', value: '0.00', trend: '0 internal-source ratio', trendType: 'neutral', detail: 'Calculated from answered source types', icon: 'target' },
    { key: 'openTickets', label: 'Open Tickets', value: '0', trend: '+ 0 active queue', trendType: 'neutral', detail: 'Open or in-progress support tickets', icon: 'ticket' },
  ],
  knowledgeCore: {
    categories: [
      'Auth / JWT',
      'Redis Cache',
      'PostgreSQL',
      'Docker',
      'Kubernetes',
      'Incident SOP',
      'Ticket Rules',
      'Customer Templates',
    ].map((category) => ({
      category,
      articleCount: 0,
      documentCount: 0,
      coveragePercent: 0,
      freshnessPercent: 0,
      icon: 'folder',
    })),
    trustedSources: { domainCount: 0, coveragePercent: 0 },
  },
  ingestionPipeline: {
    steps: ['Upload', 'Parse', 'Chunk', 'Index', 'Embed', 'Ready'].map((name, index) => ({
      name,
      status: index < 5 ? 'COMPLETED' : 'WAITING',
    })),
    recentJobs: [],
  },
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
      primaryModel: 'Not configured',
      embeddingModel: 'Not configured',
      rerankerModel: 'Not configured',
      status: 'NOT_CONFIGURED',
    },
    retrievalStrategy: {
      topKDocuments: 8,
      topKArticles: 5,
      topKWebEvidence: 5,
      rerankerEnabled: true,
    },
    webSearchSettings: {
      webFallbackThreshold: 0.65,
      trustedDomains: 0,
      maxWebResults: 0,
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

const navItems = [
  ['command', 'Command Center', Home],
  ['knowledge', 'Knowledge Library', BookOpen],
  ['documents', 'Document Library', FileText],
  ['webEvidence', 'Web Evidence', Globe2],
  ['ai', 'AI Activity', Bot],
  ['gaps', 'Knowledge Gaps', AlertTriangle],
  ['tickets', 'Tickets', GitBranch],
];

function statusText(status) {
  if (!status) return 'Unknown';
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function statusClass(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'healthy') return 'healthy';
  if (normalized === 'empty' || normalized === 'not_configured') return 'neutral';
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
  selectedEvidence,
  searchValue,
  actionStatus,
  onSearchValueChange,
  onSearch,
  onRefresh,
  onSelect,
  onApprove,
  onReject,
}) {
  const pendingCount = evidence.filter((item) => item.status === 'NEEDS_REVIEW').length;
  const convertedCount = evidence.filter((item) => item.status === 'CONVERTED').length;
  const rejectedCount = evidence.filter((item) => item.status === 'REJECTED').length;

  return (
    <section className="webEvidenceWorkspace">
      <div className="webEvidenceHero">
        <div>
          <span>External Retrieval Loop</span>
          <h2>Review web evidence before it becomes internal knowledge.</h2>
          <p>When internal knowledge misses, the assistant can collect external evidence, answer with a WEB source, and place the evidence here for administrator approval.</p>
        </div>
        <form className="webEvidenceSearch" onSubmit={onSearch}>
          <input value={searchValue} placeholder="Search external evidence for a CloudOps question..." onChange={(event) => onSearchValueChange(event.target.value)} />
          <button type="submit"><Search size={16} />Search Web</button>
        </form>
      </div>

      <div className="webEvidenceStats">
        <article><strong>{formatCount(evidence.length)}</strong><span>Total Evidence</span></article>
        <article><strong>{formatCount(pendingCount)}</strong><span>Needs Review</span></article>
        <article><strong>{formatCount(convertedCount)}</strong><span>Converted</span></article>
        <article><strong>{formatCount(rejectedCount)}</strong><span>Rejected</span></article>
      </div>

      {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}

      <div className="webEvidenceReviewGrid">
        <div className="webEvidenceQueue">
          <div className="libraryToolbar">
            <strong>{evidence.length} Evidence Records</strong>
            <button type="button" onClick={onRefresh}>Refresh</button>
          </div>
          {evidence.length ? evidence.map((item) => (
            <button className={selectedEvidence?.id === item.id ? 'active' : ''} type="button" key={item.id} onClick={() => onSelect(item)}>
              <strong>{item.title || item.question}</strong>
              <span>{item.domain || 'Unknown domain'} · {statusText(item.status)} · {item.category || 'General'}</span>
              <p>{item.question}</p>
            </button>
          )) : <EmptyState text="No external evidence yet. Ask an unknown question or run a manual web search." />}
        </div>

        <article className="webEvidenceDetail">
          {selectedEvidence ? (
            <>
              <div className="knowledgeReaderHeader">
                <span>WEB EVIDENCE</span>
                <strong>{selectedEvidence.title || selectedEvidence.question}</strong>
                <small>{selectedEvidence.domain || 'Unknown domain'} · {statusText(selectedEvidence.status)} · {formatDateTime(selectedEvidence.createdTime)}</small>
                <div className="readerActions">
                  {selectedEvidence.url && <button type="button" onClick={() => window.open(selectedEvidence.url, '_blank', 'noopener,noreferrer')}>Open Source</button>}
                  <button type="button" onClick={() => navigator.clipboard.writeText(selectedEvidence.suggestedArticleContent || selectedEvidence.extractedContent || '')}>Copy Evidence</button>
                  <button type="button" disabled={selectedEvidence.status !== 'NEEDS_REVIEW'} onClick={() => onApprove(selectedEvidence)}>Approve to Article</button>
                  <button className="danger" type="button" disabled={selectedEvidence.status !== 'NEEDS_REVIEW'} onClick={() => onReject(selectedEvidence)}>Reject</button>
                </div>
              </div>

              <div className="webEvidenceMeta">
                <InfoGroup title="Source" rows={[
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
                <h3>Suggested Internal Article</h3>
                <KnowledgeContentBlock content={selectedEvidence.suggestedArticleContent || selectedEvidence.extractedContent} />
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

function KnowledgeGaps({ data }) {
  const rows = (data?.gaps ?? []).map((gap) => [
    gap.question,
    gap.frequency,
    gap.category,
    gap.suggestedArticle,
    <span className={`priorityPill ${gap.priority?.toLowerCase()}`}>{gap.priority}</span>,
  ]);

  return <Table columns={['Question', 'Frequency', 'Category', 'Suggested Article', 'Priority']} rows={rows} empty="No knowledge gaps yet." />;
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

function isWithinDaysValue(value, days) {
  if (!value) return false;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  return Date.now() - date.getTime() <= days * 24 * 60 * 60 * 1000;
}

function formatFileSize(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 KB';
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function buildNotificationKeys(gaps = [], tickets = [], evidence = []) {
  const gapKeys = gaps.map((gap) => `gap:${gap.logId ?? gap.id ?? gap.question}`);
  const ticketKeys = tickets.map((ticket) => `ticket:${ticket.ticketId ?? ticket.id ?? ticket.title}`);
  const evidenceKeys = evidence.map((item) => `evidence:${item.evidenceId ?? item.id ?? item.queryTopic ?? item.question}`);
  return [...gapKeys, ...ticketKeys, ...evidenceKeys].filter(Boolean);
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
  const [data, setData] = useState(fallbackData);
  const [loadState, setLoadState] = useState('loading');
  const [activeModal, setActiveModal] = useState(null);
  const [actionStatus, setActionStatus] = useState({ type: '', text: '' });
  const [articleForm, setArticleForm] = useState({ title: '', category: 'General', file: null });
  const [documentForm, setDocumentForm] = useState({ category: 'General', file: null });
  const [questionForm, setQuestionForm] = useState({ question: '' });
  const [articles, setArticles] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [selectedArticle, setSelectedArticle] = useState(null);
  const [selectedDocument, setSelectedDocument] = useState(null);
  const [selectedChunks, setSelectedChunks] = useState([]);
  const [articleSearch, setArticleSearch] = useState('');
  const [documentSearch, setDocumentSearch] = useState('');
  const [globalSearch, setGlobalSearch] = useState('');
  const [globalSearchResults, setGlobalSearchResults] = useState({ articles: [], chunks: [] });
  const [articleEditor, setArticleEditor] = useState({ id: null, title: '', category: '', content: '' });
  const [aiLogs, setAiLogs] = useState([]);
  const [knowledgeGaps, setKnowledgeGaps] = useState([]);
  const [tickets, setTickets] = useState([]);
  const [webEvidence, setWebEvidence] = useState([]);
  const [selectedEvidence, setSelectedEvidence] = useState(null);
  const [webEvidenceSearch, setWebEvidenceSearch] = useState('');
  const [profile, setProfile] = useState(null);
  const [metricContext, setMetricContext] = useState(null);
  const [activePage, setActivePage] = useState('command');
  const [readNotificationKeys, setReadNotificationKeys] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('aegiscloud_read_notifications') || '[]');
    } catch {
      return [];
    }
  });
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const user = useMemo(() => {
    try {
      return JSON.parse(localStorage.getItem('aegiscloud_user') || '{}');
    } catch {
      return {};
    }
  }, []);

  const loadOperationsCenter = useCallback(() => {
    setLoadState('loading');
    getOperationsCenter()
      .then((result) => {
        setData(result || fallbackData);
        setLoadState('live');
      })
      .catch(() => {
        setData(fallbackData);
        setLoadState('fallback');
      });
  }, []);

  const filteredArticles = useMemo(() => {
    const keyword = articleSearch.trim().toLowerCase();
    if (!keyword) return articles;
    return articles.filter((article) => [
      article.title,
      article.category,
      article.content,
      article.status,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
  }, [articles, articleSearch]);

  const filteredDocuments = useMemo(() => {
    const keyword = documentSearch.trim().toLowerCase();
    if (!keyword) return documents;
    return documents.filter((document) => [
      document.originalFilename,
      document.category,
      document.contentType,
      document.status,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
  }, [documents, documentSearch]);

  const dashboardNotificationKeys = useMemo(() => buildNotificationKeys(
    data.knowledgeGapLoop?.gaps ?? [],
    data.ticketEscalation?.tickets ?? [],
    data.externalEvidence?.queue ?? []
  ), [data.knowledgeGapLoop?.gaps, data.ticketEscalation?.tickets, data.externalEvidence?.queue]);

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

  const openModal = (modal) => {
    setActionStatus({ type: '', text: '' });
    setActiveModal(modal);
  };

  const loadArticles = async () => {
    setActionStatus({ type: 'loading', text: 'Loading stored knowledge articles...' });
    try {
      const result = await getKnowledgeArticles();
      setArticles(result ?? []);
      setSelectedArticle((result ?? [])[0] ?? null);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openArticleLibrary = async () => {
    openModal('articleLibrary');
    await loadArticles();
  };

  const loadAiLogs = async () => {
    setActionStatus({ type: 'loading', text: 'Loading real AI question records...' });
    try {
      const result = await getAiQuestionLogs();
      setAiLogs(result ?? []);
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

  const loadTickets = async () => {
    setActionStatus({ type: 'loading', text: 'Loading real ticket queue...' });
    try {
      const result = await getTickets();
      setTickets(result ?? []);
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

  const loadWebEvidence = async () => {
    setActionStatus({ type: 'loading', text: 'Loading external web evidence queue...' });
    try {
      const result = await getWebEvidence();
      const loadedEvidence = result ?? [];
      setWebEvidence(loadedEvidence);
      setSelectedEvidence((current) => current && loadedEvidence.find((item) => item.id === current.id)
        ? loadedEvidence.find((item) => item.id === current.id)
        : loadedEvidence[0] ?? null);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setWebEvidence([]);
      setSelectedEvidence(null);
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openNotifications = async () => {
    openModal('notifications');
    setActionStatus({ type: 'loading', text: 'Loading real admin notifications...' });
    try {
      const [gapResults, ticketResults, evidenceResults] = await Promise.all([getKnowledgeGaps(), getTickets(), getWebEvidence()]);
      setKnowledgeGaps(gapResults ?? []);
      setTickets(ticketResults ?? []);
      setWebEvidence(evidenceResults ?? []);
      setSelectedEvidence((evidenceResults ?? [])[0] ?? null);
      markNotificationsRead(buildNotificationKeys(gapResults ?? [], ticketResults ?? [], (evidenceResults ?? []).filter((item) => item.status === 'NEEDS_REVIEW')));
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setKnowledgeGaps([]);
      setTickets([]);
      setWebEvidence([]);
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

  const loadDocuments = async () => {
    setActionStatus({ type: 'loading', text: 'Loading uploaded documents...' });
    try {
      const result = await getKnowledgeDocuments();
      const loadedDocuments = result ?? [];
      setDocuments(loadedDocuments);
      setSelectedDocument(loadedDocuments[0] ?? null);
      if (loadedDocuments[0]) {
        const chunks = await getDocumentChunks(loadedDocuments[0].id);
        setSelectedChunks(chunks ?? []);
      } else {
        setSelectedChunks([]);
      }
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const openDocumentLibrary = async () => {
    openModal('documentLibrary');
    await loadDocuments();
  };

  const viewDocumentChunks = async (document) => {
    setSelectedDocument(document);
    setActionStatus({ type: 'loading', text: 'Loading document chunks...' });
    try {
      const result = await getDocumentChunks(document.id);
      setSelectedChunks(result ?? []);
      setActionStatus({ type: '', text: '' });
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

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
    setArticles([article]);
    setSelectedArticle(article);
    setArticleSearch('');
    setActionStatus({ type: '', text: '' });
    setActiveModal('articleLibrary');
  };

  const openSearchChunk = (chunk) => {
    setDocuments([{
      id: chunk.documentId,
      originalFilename: chunk.originalFilename,
      category: chunk.category,
      fileSize: 0,
      status: 'ACTIVE',
    }]);
    setSelectedDocument({
      id: chunk.documentId,
      originalFilename: chunk.originalFilename,
      category: chunk.category,
      fileSize: 0,
      status: 'ACTIVE',
    });
    setSelectedChunks([{
      id: chunk.chunkId,
      documentId: chunk.documentId,
      chunkIndex: chunk.chunkIndex,
      content: chunk.content,
    }]);
    setDocumentSearch('');
    setActionStatus({ type: '', text: '' });
    setActiveModal('documentLibrary');
  };

  const openArticleEditor = (article) => {
    if (!article) return;
    setArticleEditor({
      id: article.id,
      title: article.title || '',
      category: article.category || '',
      content: article.content || '',
    });
    openModal('editArticle');
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

  const scrollToSection = (id) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const handleNavAction = (label) => {
    const actions = {
      command: () => {
        setActivePage('command');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      knowledge: async () => {
        setActivePage('knowledge');
        await loadArticles();
      },
      documents: async () => {
        setActivePage('documents');
        await loadDocuments();
      },
      webEvidence: async () => {
        setActivePage('webEvidence');
        await loadWebEvidence();
      },
      ai: async () => {
        setActivePage('ai');
        await loadAiLogs();
      },
      gaps: () => {
        setActivePage('gaps');
        setActionStatus({ type: '', text: '' });
      },
      tickets: async () => {
        setActivePage('tickets');
        await loadTickets();
      },
    };
    actions[label]?.();
  };

  const openGlobalSearch = () => {
    setGlobalSearchResults({ articles: [], chunks: [] });
    setActionStatus({ type: '', text: '' });
    setActiveModal('globalSearch');
  };

  const handleMetricClick = (metricKey) => {
    const actions = {
      totalKnowledgeArticles: async () => {
        setActivePage('knowledge');
        await loadArticles();
      },
      runbookDocuments: async () => {
        setActivePage('documents');
        await loadDocuments();
      },
      knowledgeCoverage: () => openModal('coverageDetail'),
      questionsAnswered7d: async () => {
        setActivePage('ai');
        await loadAiLogs();
      },
      avgAnswerConfidence: async () => {
        setActivePage('ai');
        await loadAiLogs();
      },
      openTickets: async () => {
        setActivePage('tickets');
        await loadTickets();
      },
    };
    actions[metricKey]?.();
  };

  const openWebEvidencePage = async () => {
    setActivePage('webEvidence');
    await loadWebEvidence();
  };

  const viewWebEvidenceFromSummary = async (evidenceId) => {
    setActivePage('webEvidence');
    setActionStatus({ type: 'loading', text: 'Opening external evidence record...' });
    try {
      const result = await getWebEvidence();
      const loadedEvidence = result ?? [];
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

    setActionStatus({ type: 'loading', text: 'Asking AI and recording the question log...' });
    try {
      await askAiQuestion({ question: questionForm.question.trim() });
      setActionStatus({ type: 'success', text: 'Question recorded. Dashboard metrics will reflect the new AI log.' });
      setQuestionForm({ question: '' });
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
      setSelectedArticle(updated);
      setActionStatus({ type: 'success', text: 'Article updated and ready for AI retrieval.' });
      setActiveModal('articleLibrary');
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedArticle = async () => {
    if (!selectedArticle) return;
    const confirmed = window.confirm(`Archive article "${selectedArticle.title}"? It will no longer be used as an AI answer source.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving article...' });
    try {
      await archiveKnowledgeArticle(selectedArticle.id);
      const remaining = articles.filter((article) => article.id !== selectedArticle.id);
      setArticles(remaining);
      setSelectedArticle(remaining[0] ?? null);
      setActionStatus({ type: 'success', text: 'Article archived. It is removed from active AI retrieval.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const archiveSelectedDocument = async () => {
    if (!selectedDocument) return;
    const confirmed = window.confirm(`Archive document "${selectedDocument.originalFilename}"? Its chunks will no longer be used by AI retrieval.`);
    if (!confirmed) return;

    setActionStatus({ type: 'loading', text: 'Archiving document...' });
    try {
      await archiveKnowledgeDocument(selectedDocument.id);
      const remaining = documents.filter((document) => document.id !== selectedDocument.id);
      setDocuments(remaining);
      setSelectedDocument(remaining[0] ?? null);
      if (remaining[0]) {
        const chunks = await getDocumentChunks(remaining[0].id);
        setSelectedChunks(chunks ?? []);
      } else {
        setSelectedChunks([]);
      }
      setActionStatus({ type: 'success', text: 'Document archived. Its chunks are removed from active retrieval.' });
      loadOperationsCenter();
    } catch (error) {
      setActionStatus({ type: 'error', text: error.message });
    }
  };

  const renderCommandCenterPage = () => (
    <>
      <section className="metricsGrid">
        {(data.metrics ?? []).map((metric) => <MetricCard metric={metric} key={metric.key} onClick={() => handleMetricClick(metric.key)} />)}
      </section>

      <section className="commandCenterGrid">
        <article className="commandPanel highlight">
          <span>Primary Workflow</span>
          <h2>Build reliable internal knowledge first.</h2>
          <p>Upload verified runbooks, review gaps, and let AI answer only from grounded sources.</p>
          <div>
            <button type="button" onClick={() => openModal('uploadArticle')}><FileText size={15} />New Article</button>
            <button type="button" onClick={() => openModal('uploadDocument')}><Upload size={15} />Upload Document</button>
          </div>
        </article>

        <article className="commandPanel">
          <span>Current Queue</span>
          <h3>{data.knowledgeGapLoop?.gaps?.length ?? 0} gaps · {data.ticketEscalation?.tickets?.length ?? 0} tickets</h3>
          <p>Work items here come from real AI misses and open support tickets.</p>
          <div>
            <button type="button" onClick={() => setActivePage('gaps')}>Review Gaps</button>
            <button type="button" onClick={async () => { setActivePage('tickets'); await loadTickets(); }}>Open Tickets</button>
          </div>
        </article>

        <article className="commandPanel web">
          <span>External Evidence</span>
          <h3>{data.externalEvidence?.summary?.pendingReview ?? 0} pending review</h3>
          <p>External evidence is captured when internal knowledge cannot answer and waits for admin approval.</p>
          <div>
            <button type="button" onClick={openWebEvidencePage}>Review Evidence</button>
            <button type="button" onClick={openWebEvidencePage}>Search Web</button>
          </div>
        </article>

        <article className="commandPanel">
          <span>Knowledge Access</span>
          <h3>Search and manage stored sources.</h3>
          <p>Use the libraries for maintenance. Use search when you need a precise source quickly.</p>
          <div>
            <button type="button" onClick={openGlobalSearch}>Search</button>
            <button type="button" onClick={async () => { setActivePage('knowledge'); await loadArticles(); }}>Articles</button>
            <button type="button" onClick={async () => { setActivePage('documents'); await loadDocuments(); }}>Documents</button>
          </div>
        </article>
      </section>

      <section className="commandInsightGrid">
        <article className="insightPanel">
          <div className="insightHeader">
            <span>Coverage Map</span>
            <button type="button" onClick={() => openModal('coverageDetail')}>Details</button>
          </div>
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
            <span>Source Mix</span>
            <button type="button" onClick={async () => { setActivePage('ai'); await loadAiLogs(); }}>Open AI Activity</button>
          </div>
          <div className="sourceMixCards">
            {(data.reasoningLayer?.sourceMix ?? []).length ? data.reasoningLayer.sourceMix.map((source) => (
              <div key={source.source}>
                <strong>{source.source}</strong>
                <span>{source.count ?? 0}</span>
                <i>{source.percent ?? 0}%</i>
              </div>
            )) : <EmptyState text="No AI source data yet." />}
          </div>
        </article>

        <article className="insightPanel">
          <div className="insightHeader">
            <span>Attention Queue</span>
            <button type="button" onClick={openNotifications}>Review</button>
          </div>
          <div className="attentionList">
            {(data.knowledgeGapLoop?.gaps ?? []).slice(0, 2).map((gap) => (
              <button type="button" key={gap.logId} onClick={() => setActivePage('gaps')}>
                <strong>{gap.priority}</strong>
                <span>{gap.question}</span>
              </button>
            ))}
            {(data.ticketEscalation?.tickets ?? []).slice(0, 2).map((ticket) => (
              <button type="button" key={ticket.ticketId} onClick={async () => { setActivePage('tickets'); await loadTickets(); }}>
                <strong>{ticket.priority}</strong>
                <span>{ticket.title}</span>
              </button>
            ))}
            {!(data.knowledgeGapLoop?.gaps ?? []).length && !(data.ticketEscalation?.tickets ?? []).length && <EmptyState text="No urgent work items." />}
          </div>
        </article>

        <article className="insightPanel wideInsight">
          <div className="insightHeader">
            <span>Web Evidence Loop</span>
            <button type="button" onClick={openWebEvidencePage}>Open</button>
          </div>
          <ExternalEvidence data={data.externalEvidence} onOpen={openWebEvidencePage} onView={viewWebEvidenceFromSummary} />
        </article>
      </section>
    </>
  );

  const renderKnowledgePage = () => (
    <section className="adminPageSurface">
      <div className="adminPageHeader">
        <div>
          <span>Knowledge Library</span>
          <h2>Published Articles</h2>
          <p>Maintain concise internal articles used by AI as structured answer sources.</p>
        </div>
        <div>
          <button type="button" onClick={loadArticles}>Refresh</button>
          <button type="button" onClick={() => openModal('uploadArticle')}>New Article</button>
        </div>
      </div>
      <div className="knowledgeLibrary embedded">
        <div className="libraryList">
          <div className="libraryToolbar">
            <strong>{articles.length} Articles</strong>
            <button type="button" onClick={() => openModal('uploadArticle')}>Upload New</button>
          </div>
          <input className="librarySearch" value={articleSearch} placeholder="Search title, category, content..." onChange={(event) => setArticleSearch(event.target.value)} />
          {actionStatus.text && activePage === 'knowledge' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          {filteredArticles.length ? filteredArticles.map((article) => (
            <button className={selectedArticle?.id === article.id ? 'active' : ''} type="button" key={article.id} onClick={() => setSelectedArticle(article)}>
              <strong>{article.title}</strong>
              <span>{article.category || 'Uncategorized'} · {statusText(article.status)}</span>
            </button>
          )) : <EmptyState text="No knowledge articles uploaded yet." />}
        </div>
        <KnowledgeReader
          article={selectedArticle}
          actions={selectedArticle && (
            <>
              <button type="button" onClick={() => openArticleEditor(selectedArticle)}>Edit</button>
              <button type="button" onClick={() => copyToClipboard(selectedArticle.content, 'Article content copied.')}>Copy</button>
              <button className="danger" type="button" onClick={archiveSelectedArticle}>Archive</button>
            </>
          )}
        />
      </div>
    </section>
  );

  const renderDocumentPage = () => (
    <section className="adminPageSurface">
      <div className="adminPageHeader">
        <div>
          <span>Document Library</span>
          <h2>Uploaded Runbooks</h2>
          <p>Inspect uploaded documents and the exact chunks used by retrieval.</p>
        </div>
        <div>
          <button type="button" onClick={loadDocuments}>Refresh</button>
          <button type="button" onClick={() => openModal('uploadDocument')}>Upload Document</button>
        </div>
      </div>
      <div className="knowledgeLibrary embedded">
        <div className="libraryList">
          <div className="libraryToolbar">
            <strong>{documents.length} Documents</strong>
            <button type="button" onClick={() => openModal('uploadDocument')}>Upload New</button>
          </div>
          <input className="librarySearch" value={documentSearch} placeholder="Search filename, category, type..." onChange={(event) => setDocumentSearch(event.target.value)} />
          {actionStatus.text && activePage === 'documents' && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          {filteredDocuments.length ? filteredDocuments.map((document) => (
            <button className={selectedDocument?.id === document.id ? 'active' : ''} type="button" key={document.id} onClick={() => viewDocumentChunks(document)}>
              <strong>{document.originalFilename}</strong>
              <span>{document.category || 'Uncategorized'} · {formatFileSize(document.fileSize)} · {statusText(document.status)}</span>
            </button>
          )) : <EmptyState text="No documents uploaded yet." />}
        </div>
        <KnowledgeReader
          chunks={selectedChunks}
          actions={selectedDocument && (
            <>
              <button type="button" onClick={() => viewDocumentChunks(selectedDocument)}>Refresh Chunks</button>
              <button type="button" onClick={() => copyToClipboard(selectedChunks.map((chunk) => chunk.content).join('\n\n'), 'Document chunks copied.')}>Copy Chunks</button>
              <button className="danger" type="button" onClick={archiveSelectedDocument}>Archive</button>
            </>
          )}
        />
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
          <button type="button" onClick={loadAiLogs}>Refresh Logs</button>
          <button type="button" onClick={() => openModal('ask')}>Ask AI</button>
        </div>
      </div>
      <div className="pageSplitGrid">
        <ReasoningLayer data={data.reasoningLayer} />
        <div className="topRecordList">
          {aiLogs.length ? aiLogs.map((log) => (
            <article key={log.id}>
              <strong>{log.question}</strong>
              <span>{statusText(log.sourceType)} · {formatDateTime(log.createdTime)}</span>
              <p>{log.answer}</p>
            </article>
          )) : <EmptyState text="No AI question logs loaded yet." />}
        </div>
      </div>
    </section>
  );

  const renderWebEvidencePage = () => (
    <WebEvidenceWorkspace
      evidence={webEvidence}
      selectedEvidence={selectedEvidence}
      searchValue={webEvidenceSearch}
      actionStatus={activePage === 'webEvidence' ? actionStatus : { type: '', text: '' }}
      onSearchValueChange={setWebEvidenceSearch}
      onSearch={submitWebEvidenceSearch}
      onRefresh={loadWebEvidence}
      onSelect={setSelectedEvidence}
      onApprove={approveSelectedEvidence}
      onReject={rejectSelectedEvidence}
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
          <button type="button" onClick={loadOperationsCenter}>Refresh</button>
        </div>
      </div>
      <KnowledgeGaps data={data.knowledgeGapLoop} />
    </section>
  );

  const renderTicketsPage = () => {
    const openTicketRows = tickets.length ? tickets : (data.ticketEscalation?.tickets ?? []);
    return (
      <section className="adminPageSurface">
        <div className="adminPageHeader">
          <div>
            <span>Tickets</span>
            <h2>Support Escalation Queue</h2>
            <p>Track real open support work created by users or AI escalation.</p>
          </div>
          <div>
            <button type="button" onClick={loadTickets}>Refresh Tickets</button>
          </div>
        </div>
        <div className="topRecordList full">
          {openTicketRows.length ? openTicketRows.map((ticket) => (
            <article key={ticket.id || ticket.title}>
              <strong>{ticket.title}</strong>
              <span>{ticket.priority} · {ticket.status || ticket.owner || 'Open'} · {formatDateTime(ticket.createdTime)}</span>
              <p>{ticket.description || `Owner: ${ticket.owner || 'Unassigned'} · SLA: ${ticket.sla || '-'}`}</p>
            </article>
          )) : <EmptyState text="No open tickets." />}
        </div>
      </section>
    );
  };

  const renderActivePage = () => {
    if (activePage === 'knowledge') return renderKnowledgePage();
    if (activePage === 'documents') return renderDocumentPage();
    if (activePage === 'webEvidence') return renderWebEvidencePage();
    if (activePage === 'ai') return renderAiPage();
    if (activePage === 'gaps') return renderGapsPage();
    if (activePage === 'tickets') return renderTicketsPage();
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
        <AdminModal title="Ask AI With Current Knowledge" onClose={closeModal}>
          <form className="adminActionForm" onSubmit={submitAiQuestion}>
            <Field label="Question">
              <textarea value={questionForm.question} rows={5} placeholder="Ask about uploaded documents or knowledge articles" onChange={(event) => setQuestionForm({ question: event.target.value })} />
            </Field>
            <button className="adminSubmitButton" type="submit">Ask and Record</button>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
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
        <AdminModal title="Edit Knowledge Article" onClose={() => setActiveModal('articleLibrary')}>
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
              <button className="secondaryAdminButton" type="button" onClick={() => setActiveModal('articleLibrary')}>Cancel</button>
            </div>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
          </form>
        </AdminModal>
      );
    }

    if (activeModal === 'articleLibrary') {
      return (
        <AdminModal title="Knowledge Articles" onClose={closeModal}>
          <div className="knowledgeLibrary">
            <div className="libraryList">
              <div className="libraryToolbar">
                <strong>{articles.length} Articles</strong>
                <button type="button" onClick={() => openModal('uploadArticle')}>Upload New</button>
              </div>
              <input className="librarySearch" value={articleSearch} placeholder="Search title, category, content..." onChange={(event) => setArticleSearch(event.target.value)} />
              {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
              {filteredArticles.length ? filteredArticles.map((article) => (
                <button
                  className={selectedArticle?.id === article.id ? 'active' : ''}
                  type="button"
                  key={article.id}
                  onClick={() => setSelectedArticle(article)}
                >
                  <strong>{article.title}</strong>
                  <span>{article.category || 'Uncategorized'} · {statusText(article.status)}</span>
                </button>
              )) : <EmptyState text="No knowledge articles uploaded yet." />}
            </div>
            <KnowledgeReader
              article={selectedArticle}
              actions={selectedArticle && (
                <>
                  <button type="button" onClick={() => openArticleEditor(selectedArticle)}>Edit</button>
                  <button type="button" onClick={() => copyToClipboard(selectedArticle.content, 'Article content copied.')}>Copy</button>
                  <button className="danger" type="button" onClick={archiveSelectedArticle}>Archive</button>
                </>
              )}
            />
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'documentLibrary') {
      return (
        <AdminModal title="Uploaded Runbook Documents" onClose={closeModal}>
          <div className="knowledgeLibrary">
            <div className="libraryList">
              <div className="libraryToolbar">
                <strong>{documents.length} Documents</strong>
                <button type="button" onClick={() => openModal('uploadDocument')}>Upload New</button>
              </div>
              <input className="librarySearch" value={documentSearch} placeholder="Search filename, category, type..." onChange={(event) => setDocumentSearch(event.target.value)} />
              {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
              {filteredDocuments.length ? filteredDocuments.map((document) => (
                <button
                  className={selectedDocument?.id === document.id ? 'active' : ''}
                  type="button"
                  key={document.id}
                  onClick={() => viewDocumentChunks(document)}
                >
                  <strong>{document.originalFilename}</strong>
                  <span>{document.category || 'Uncategorized'} · {formatFileSize(document.fileSize)} · {statusText(document.status)}</span>
                </button>
              )) : <EmptyState text="No documents uploaded yet." />}
            </div>
            <KnowledgeReader
              chunks={selectedChunks}
              actions={selectedDocument && (
                <>
                  <button type="button" onClick={() => viewDocumentChunks(selectedDocument)}>Refresh Chunks</button>
                  <button type="button" onClick={() => copyToClipboard(selectedChunks.map((chunk) => chunk.content).join('\n\n'), 'Document chunks copied.')}>Copy Chunks</button>
                  <button className="danger" type="button" onClick={archiveSelectedDocument}>Archive</button>
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
      const filteredLogs = metricContext === 'questions'
        ? aiLogs.filter((log) => isWithinDaysValue(log.createdTime, 7))
        : aiLogs;
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
              {filteredLogs.length ? filteredLogs.map((log) => (
                <article key={log.id}>
                  <strong>{log.question}</strong>
                  <span>{statusText(log.sourceType)} · {formatDateTime(log.createdTime)}</span>
                  <p>{log.answer}</p>
                </article>
              )) : <EmptyState text="No matching AI records yet." />}
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'ticketList') {
      const openTicketRows = tickets.filter((ticket) => ['OPEN', 'IN_PROGRESS'].includes(ticket.status));
      return (
        <AdminModal title="Open Ticket Queue" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">Open tickets are loaded from the real ticket queue and update as tickets are created or resolved.</p>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="topRecordList">
              {openTicketRows.length ? openTicketRows.map((ticket) => (
                <article key={ticket.id}>
                  <strong>{ticket.title}</strong>
                  <span>{ticket.priority} · {ticket.status} · {formatDateTime(ticket.createdTime)}</span>
                  <p>{ticket.description}</p>
                </article>
              )) : <EmptyState text="No open tickets." />}
            </div>
          </div>
        </AdminModal>
      );
    }

    if (activeModal === 'notifications') {
      const openTicketRows = tickets.filter((ticket) => ['OPEN', 'IN_PROGRESS'].includes(ticket.status));
      return (
        <AdminModal title="Admin Notifications" onClose={closeModal}>
          <div className="topDetailPanel">
            <p className="topDetailLead">Notifications are generated from real knowledge gaps and open support tickets.</p>
            {actionStatus.text && <div className={`adminActionStatus ${actionStatus.type}`}>{actionStatus.text}</div>}
            <div className="notificationGroups">
              <section>
                <h3>Knowledge Gaps</h3>
                {knowledgeGaps.length ? knowledgeGaps.map((gap) => (
                  <button type="button" key={gap.logId} onClick={() => { setActivePage('gaps'); closeModal(); }}>
                    <strong>{gap.question}</strong>
                    <span>{formatDateTime(gap.createdTime)}</span>
                  </button>
                )) : <EmptyState text="No knowledge gaps." />}
              </section>
              <section>
                <h3>Open Tickets</h3>
                {openTicketRows.length ? openTicketRows.map((ticket) => (
                  <button type="button" key={ticket.id} onClick={() => { setActivePage('tickets'); closeModal(); }}>
                    <strong>{ticket.title}</strong>
                    <span>{ticket.priority} · {ticket.status}</span>
                  </button>
                )) : <EmptyState text="No open tickets." />}
              </section>
              <section>
                <h3>Web Evidence</h3>
                {webEvidence.filter((item) => item.status === 'NEEDS_REVIEW').length ? webEvidence.filter((item) => item.status === 'NEEDS_REVIEW').map((item) => (
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
              ]} />
              <InfoGroup title="Retrieval" rows={[
                ['Top-K Documents', retrieval.topKDocuments],
                ['Top-K Articles', retrieval.topKArticles],
                ['Reranker Enabled', retrieval.rerankerEnabled ? 'On' : 'Off'],
                ['Web Threshold', web.webFallbackThreshold],
              ]} />
            </div>
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
      <aside className="adminSidebar">
        <div className="adminLogo">
          <Cloud size={38} />
          <div>
            <strong>AegisCloud</strong>
            <span>CloudOps Support Platform</span>
          </div>
        </div>
        <nav className="adminNav">
          {navItems.map(([id, label, Icon]) => (
            <button className={activePage === id ? 'active' : ''} type="button" key={id} onClick={() => handleNavAction(id)}>
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="quickActions">
          <div className="quickActionsHeader">
            <strong>Quick Actions</strong>
            <span>Live</span>
          </div>
          <button className="primary" type="button" onClick={() => openModal('uploadArticle')}><FileText size={15} />New Article</button>
          <button className="primary" type="button" onClick={() => openModal('uploadDocument')}><Upload size={15} />Upload Document</button>
          <button className="primary" type="button" onClick={openWebEvidencePage}><Globe2 size={15} />Web Evidence</button>
          <button type="button" onClick={openGlobalSearch}><Search size={15} />Search Knowledge</button>
          <button type="button" onClick={async () => { setActivePage('knowledge'); await loadArticles(); }}><BookOpen size={15} />Manage Articles</button>
          <button type="button" onClick={async () => { setActivePage('documents'); await loadDocuments(); }}><FileText size={15} />Manage Documents</button>
          <button type="button" onClick={() => setActivePage('gaps')}><Target size={15} />Review Gaps</button>
          <button type="button" onClick={async () => { setActivePage('tickets'); await loadTickets(); }}><Ticket size={15} />Open Tickets</button>
        </div>
        <button className="lastUpdated" type="button" onClick={loadOperationsCenter}>
          <span>Last Updated</span>
          <strong>{new Date().toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</strong>
          <RefreshCw size={16} />
        </button>
        <button className="collapseButton" type="button" onClick={() => setSidebarCollapsed((value) => !value)}><PanelLeftClose size={18} />Collapse</button>
      </aside>

      <main className="adminMain">
        <header className="adminTopSection">
          <div className="adminTopbar">
            <div className="adminTitleBlock">
              <span className="adminEyebrow">Command Center</span>
              <h1>{data.title}</h1>
              <p>{data.subtitle}</p>
            </div>
            <div className="topbarControls">
              <button className={`environmentPill ${statusClass(data.environment?.status)}`} type="button" onClick={() => openModal('environmentInfo')}>
                <span />{data.environment?.name ?? 'Production'}<ChevronDown size={14} />
              </button>
              <form className="adminSearch" onSubmit={submitGlobalSearch}>
                <input value={globalSearch} placeholder="Search stored knowledge..." onChange={(event) => setGlobalSearch(event.target.value)} />
                <button type="submit" aria-label="Search stored knowledge"><Search size={18} /></button>
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

          <div className="serviceHealthBar">
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
