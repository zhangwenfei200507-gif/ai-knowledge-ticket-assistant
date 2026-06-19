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
import { askAiQuestion, getOperationsCenter, uploadKnowledgeArticle, uploadKnowledgeDocument } from './api/admin';

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
    { key: 'totalKnowledgeArticles', label: 'Total Knowledge Articles', value: '0', trend: '+ 0 this week', icon: 'article' },
    { key: 'runbookDocuments', label: 'Runbook Documents', value: '0', trend: '+ 0 this week', icon: 'book' },
    { key: 'knowledgeCoverage', label: 'Knowledge Coverage', value: '0%', trend: 'based on core domains', icon: 'coverage' },
    { key: 'questionsAnswered7d', label: 'Questions Answered (7D)', value: '0', trend: '+ 0 last 7 days', icon: 'question' },
    { key: 'avgAnswerConfidence', label: 'Avg Answer Confidence', value: '0.00', trend: 'from internal-source ratio', icon: 'target' },
    { key: 'openTickets', label: 'Open Tickets', value: '0', trend: '+ 0 active queue', icon: 'ticket' },
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
  ['Command Center', Home],
  ['User Document Q&A', FileText],
  ['Runbooks', CalendarDays],
  ['Knowledge Base', Bot],
  ['Web Evidence', Globe2],
  ['LLM Reasoning', Network],
  ['Knowledge Gaps', AlertTriangle],
  ['Tickets', GitBranch],
  ['Trusted Sources', ShieldCheck],
  ['Model Settings', Settings],
];

function statusText(status) {
  if (!status) return 'Unknown';
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
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

function MetricCard({ metric }) {
  const Icon = metricIconMap[metric.icon] || Gauge;

  return (
    <article className="adminMetricCard">
      <div className="adminMetricIcon"><Icon size={24} /></div>
      <div>
        <p>{metric.label}</p>
        <strong>{metric.value}</strong>
        <span>{metric.trend}</span>
      </div>
    </article>
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

function ExternalEvidence({ data }) {
  const queue = data?.queue ?? [];
  const summary = data?.summary ?? {};

  return (
    <>
      <Table
        columns={['Query / Topic', 'Top Domain', 'Credibility', 'Status', 'Action']}
        empty="External web evidence is not configured yet."
        rows={queue.map((item) => [
          item.queryTopic,
          item.topDomain,
          item.credibility,
          <span className="reviewPill">{statusText(item.status)}</span>,
          <button className="iconOnlyButton" type="button"><Eye size={14} /></button>,
        ])}
      />
      <div className="summaryBox">
        <strong>AI Search Summary <span>(Today)</span></strong>
        <div>
          <p><b>{summary.searches ?? 0}</b><span>Searches</span></p>
          <p><b>{summary.usefulEvidence ?? 0}</b><span>Useful Evidence</span></p>
          <p><b>{summary.convertedToArticles ?? 0}</b><span>Converted to Articles</span></p>
          <p><b className="dangerText">{summary.pendingReview ?? 0}</b><span>Pending Review</span></p>
        </div>
      </div>
    </>
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

  useEffect(() => {
    loadOperationsCenter();
  }, [loadOperationsCenter]);

  const openModal = (modal) => {
    setActionStatus({ type: '', text: '' });
    setActiveModal(modal);
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
      'Command Center': () => window.scrollTo({ top: 0, behavior: 'smooth' }),
      'User Document Q&A': () => openModal('ask'),
      Runbooks: () => openModal('uploadDocument'),
      'Knowledge Base': () => openModal('uploadArticle'),
      'Web Evidence': () => openModal('webEvidenceUnavailable'),
      'LLM Reasoning': () => scrollToSection('ai-reasoning-layer'),
      'Knowledge Gaps': () => scrollToSection('knowledge-gap-loop'),
      Tickets: () => scrollToSection('ticket-escalation'),
      'Trusted Sources': () => openModal('trustedSourcesUnavailable'),
      'Model Settings': () => scrollToSection('model-search-controls'),
    };
    actions[label]?.();
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

    const capabilityMessages = {
      webEvidenceUnavailable: ['External Web Evidence', 'Web search, trusted-domain scoring, and evidence review are not configured yet. The queue stays empty until the real web evidence service is implemented.'],
      trustedSourcesUnavailable: ['Trusted Sources', 'Trusted domain management needs a dedicated trusted_sources table and review workflow. No fake trusted domains are created.'],
      notificationsUnavailable: ['Notifications', 'Notification storage and delivery are not implemented yet. No fake notifications are shown.'],
      environmentInfo: ['Environment', 'Production is the active local environment label. Environment switching will connect when multi-environment config exists.'],
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
          {navItems.map(([label, Icon], index) => (
            <button className={index === 0 ? 'active' : ''} type="button" key={label} onClick={() => handleNavAction(label)}>
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="quickActions">
          <strong>Quick Actions</strong>
          <button type="button" onClick={() => openModal('uploadArticle')}><FileText size={15} />New Article</button>
          <button type="button" onClick={() => openModal('uploadDocument')}><Upload size={15} />Upload Document</button>
          <button type="button" onClick={() => openModal('webEvidenceUnavailable')}><Search size={15} />Search Web Evidence</button>
          <button type="button" onClick={() => scrollToSection('knowledge-gap-loop')}><Target size={15} />Review Knowledge Gap</button>
          <button type="button" onClick={() => openModal('documentationUnavailable')}><Megaphone size={15} />Create Announcement</button>
        </div>
        <button className="lastUpdated" type="button" onClick={loadOperationsCenter}>
          <span>Last Updated</span>
          <strong>{new Date().toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</strong>
          <RefreshCw size={16} />
        </button>
        <button className="collapseButton" type="button" onClick={() => setSidebarCollapsed((value) => !value)}><PanelLeftClose size={18} />Collapse</button>
      </aside>

      <main className="adminMain">
        <header className="adminTopbar">
          <div>
            <h1>{data.title}</h1>
            <p>{data.subtitle}</p>
          </div>
          <div className="topbarControls">
            <button className="environmentPill" type="button" onClick={() => openModal('environmentInfo')}><span />{data.environment?.name ?? 'Production'}<ChevronDown size={14} /></button>
            <div className="serviceHealthBar">
              {(data.serviceHealth ?? []).map((service) => (
                <p key={service.name}><span className={service.status === 'HEALTHY' ? '' : 'warning'} />{service.name}<b>{statusText(service.status)}</b></p>
              ))}
            </div>
            <label className="adminSearch">
              <input placeholder="Search knowledge, tickets..." onFocus={() => openModal('ask')} readOnly />
              <Search size={18} />
            </label>
            <button className="notificationButton" type="button" onClick={() => openModal('notificationsUnavailable')}><Bell size={19} /><i>0</i></button>
            <div className="adminProfile">
              <div><UserRound size={18} /></div>
              <p><strong>{user.displayName || 'Admin'}</strong><span>Platform Admin</span></p>
              <ChevronDown size={14} />
            </div>
          </div>
        </header>

        {loadState === 'fallback' && (
          <div className="adminBanner">Backend data is unavailable. Showing empty structure only; no data is fabricated.</div>
        )}

        <section className="metricsGrid">
          {(data.metrics ?? []).map((metric) => <MetricCard metric={metric} key={metric.key} />)}
        </section>

        <section className="operationsGrid">
          <Section number="1" title="CloudOps Knowledge Core" action="New Article" onAction={() => openModal('uploadArticle')} className="span2" id="knowledge-core">
            <KnowledgeCore data={data.knowledgeCore} />
          </Section>
          <Section number="2" title="Document Ingestion Pipeline" action="Upload" onAction={() => openModal('uploadDocument')} className="span2" id="document-ingestion">
            <IngestionPipeline data={data.ingestionPipeline} />
          </Section>
          <Section number="4" title="External Web Evidence Queue" action="Configure" onAction={() => openModal('webEvidenceUnavailable')} className="span2" id="external-evidence">
            <ExternalEvidence data={data.externalEvidence} />
          </Section>
          <Section number="3" title="AI Reasoning Layer" action="Ask AI" onAction={() => openModal('ask')} className="span2" id="ai-reasoning-layer">
            <ReasoningLayer data={data.reasoningLayer} />
          </Section>
          <Section number="5" title="Knowledge Gap Loop" action="Review All" onAction={() => scrollToSection('knowledge-gap-loop')} className="span2" id="knowledge-gap-loop">
            <KnowledgeGaps data={data.knowledgeGapLoop} />
          </Section>
          <Section number="6" title="Support Automation - Ticket Escalation Queue" action="View All" onAction={() => scrollToSection('ticket-escalation')} className="span2" id="ticket-escalation">
            <Tickets data={data.ticketEscalation} />
          </Section>
          <Section number="7" title="Knowledge & Answer Analytics" action="Refresh" onAction={loadOperationsCenter} className="span3" id="answer-analytics">
            <Analytics data={data.answerAnalytics} />
          </Section>
          <Section number="8" title="Model & Search Controls" action="Configure" onAction={() => openModal('webEvidenceUnavailable')} className="span3" id="model-search-controls">
            <ModelControls data={data.modelSearchControls} />
          </Section>
        </section>

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
