import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity,
  ArrowRight,
  Check,
  CircleAlert,
  Code2,
  Copy,
  Database,
  FileText,
  Github,
  LogOut,
  Shield,
  Sparkles,
  UploadCloud,
  X,
} from 'lucide-react';
import { exchangeOAuthCode, getOAuthProviders } from './api/auth';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import AdminOperationsCenter from './AdminOperationsCenter';
import './styles.css';

const clearSession = () => {
  localStorage.removeItem('aegiscloud_token');
  localStorage.removeItem('aegiscloud_user');
  localStorage.removeItem('aegiscloud_entry');
};

const readStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('aegiscloud_user') || 'null');
  } catch {
    return null;
  }
};

const safeExternalUrl = (value) => {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
  } catch {
    return null;
  }
};

const isUnavailableAnswer = (value) => [value?.answerMode, value?.sourceType]
  .some((item) => String(item || '').toUpperCase() === 'NONE');

function StatusItem({ icon: Icon, label, state }) {
  const stateText = state === 'up' ? 'Connected' : state === 'down' ? 'Unavailable' : 'Checking';
  return (
    <div className={`statusItem ${state}`}>
      <div className="statusIcon"><Icon size={19} strokeWidth={1.7} /></div>
      <div>
        <strong>{label}</strong>
        <span><i />{stateText}</span>
      </div>
    </div>
  );
}

function UserWorkspace({ onSignOut }) {
  const user = readStoredUser();
  const [question, setQuestion] = useState('');
  const [file, setFile] = useState(null);
  const [answer, setAnswer] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [copied, setCopied] = useState(false);
  const fileInputRef = useRef(null);

  const submitQuestion = async (event) => {
    event.preventDefault();
    if (!question.trim() || submitting) return;
    setSubmitting(true);
    setError('');
    setAnswer(null);
    try {
      const token = localStorage.getItem('aegiscloud_token');
      const headers = { Authorization: `Bearer ${token}` };
      let request;
      if (file) {
        const body = new FormData();
        body.append('question', question.trim());
        body.append('file', file);
        request = fetch('/api/ai/ask-with-file', { method: 'POST', headers, body });
      } else {
        headers['Content-Type'] = 'application/json';
        request = fetch('/api/ai/ask', {
          method: 'POST',
          headers,
          body: JSON.stringify({ question: question.trim() }),
        });
      }
      const response = await request;
      const payload = await response.json().catch(() => null);
      if (!response.ok || payload?.code === 0 || !payload?.data) {
        throw new Error(payload?.message || 'The assistant could not complete this request.');
      }
      setAnswer(payload.data);
      if (isUnavailableAnswer(payload.data)) {
        setError('Insufficient evidence. The assistant could not produce a reliable answer yet.');
      }
    } catch (requestError) {
      setError(requestError.message || 'The assistant could not complete this request.');
    } finally {
      setSubmitting(false);
    }
  };

  const copyAnswer = async () => {
    if (!answer?.answer || !navigator.clipboard) return;
    try {
      await navigator.clipboard.writeText(answer.answer);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setError('The answer could not be copied. Select the text and copy it manually.');
    }
  };

  return (
    <main className="workspaceShell">
      <header className="workspaceTopbar">
        <a className="workspaceBrand" href="/workspace" aria-label="CloudOps workspace">
          <span className="workspaceBrandMark"><Shield size={23} /><b>A</b></span>
          <span><strong>CloudOps</strong><small>Knowledge Base &amp; Ticket Assistant</small></span>
        </a>
        <div className="workspaceUser">
          <span className="workspaceAvatar">{(user?.displayName || user?.username || 'U').slice(0, 1).toUpperCase()}</span>
          <span className="workspaceUserName">{user?.displayName || user?.username || 'Workspace user'}</span>
          <button className="workspaceSignOut" type="button" onClick={onSignOut} title="Sign out" aria-label="Sign out">
            <LogOut size={17} />
          </button>
        </div>
      </header>

      <section className="workspaceContent">
        <div className="workspaceIntro">
          <span className="workspaceEyebrow"><Sparkles size={15} /> DOCUMENT INTELLIGENCE</span>
          <h1>Ask with confidence.</h1>
          <p>Ask a CloudOps question or add a document to ground the answer in your own material.</p>
        </div>

        <form className="workspaceAsk" onSubmit={submitQuestion}>
          <label htmlFor="workspace-question">Your question</label>
          <textarea
            id="workspace-question"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="Ask about an incident, configuration, or the document you attach..."
            rows={5}
            maxLength={4000}
            required
          />
          <div className="workspaceAskFooter">
            <div className="workspaceFileControl">
              <label htmlFor="workspace-file" className="workspaceFileButton">
                <UploadCloud size={17} />
                {file ? 'Change document' : 'Add a document'}
              </label>
              <input
                ref={fileInputRef}
                id="workspace-file"
                type="file"
                accept=".pdf,.docx,.txt,.md,.markdown"
                onChange={(event) => {
                  const selected = event.target.files?.[0] || null;
                  if (selected && selected.size > 10 * 1024 * 1024) {
                    setError('The selected document is larger than the 10 MB limit.');
                    event.target.value = '';
                    setFile(null);
                    return;
                  }
                  setError('');
                  setFile(selected);
                }}
              />
              {file && (
                <span className="workspaceFileName" title={file.name}>
                  <FileText size={15} />{file.name}
                  <button type="button" aria-label="Remove document" onClick={() => {
                    setFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = '';
                  }}><X size={15} /></button>
                </span>
              )}
              <span className="workspaceFileHint">PDF, DOCX, TXT, MD · up to 10 MB</span>
            </div>
            <button className="workspaceSubmit" type="submit" disabled={!question.trim() || submitting}>
              {submitting ? 'Thinking...' : 'Ask AI'}
              {!submitting && <ArrowRight size={18} />}
            </button>
          </div>
          {error && <div className="workspaceMessage error"><CircleAlert size={17} />{error}</div>}
        </form>

        {submitting && (
          <section className="workspaceAnswer loading" aria-live="polite">
            <span className="workspaceAnswerSignal"><Activity size={16} /> Working through your question</span>
            <div className="workspaceProgress"><i /></div>
          </section>
        )}
        {answer && (
          <section className="workspaceAnswer" aria-live="polite">
            <div className="workspaceAnswerHeader">
              <span className="workspaceAnswerSignal"><Check size={16} /> {isUnavailableAnswer(answer) ? 'INSUFFICIENT EVIDENCE' : 'ANSWER'}</span>
              <div className="workspaceAnswerActions">
                <span className="workspaceSourceType">{isUnavailableAnswer(answer) ? 'NO RELIABLE ANSWER' : answer.answerMode || answer.sourceType || 'UNVERIFIED'}</span>
                <button type="button" className="workspaceCopy" onClick={copyAnswer} aria-label="Copy answer" title="Copy answer">
                  {copied ? <Check size={15} /> : <Copy size={15} />}
                  {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
            </div>
            <div className="workspaceAnswerText">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{ table: ({ node, ...props }) => <div className="workspaceAnswerTable"><table {...props} /></div> }}
              >
                {answer.answer || ''}
              </ReactMarkdown>
            </div>
            {Array.isArray(answer.sources) && answer.sources.length > 0 && (
              <details className="workspaceSources">
                <summary>Sources ({answer.sources.length})</summary>
                <ul>{answer.sources.map((source, index) => {
                  const sourceUrl = safeExternalUrl(source.sourceUrl);
                  return (
                    <li key={`${source.sourceArticleId || source.sourceDocumentId || index}-${index}`}>
                      {sourceUrl
                        ? <a href={sourceUrl} target="_blank" rel="noreferrer">{source.sourceTitle || source.sourceDomain || 'Web reference'}</a>
                        : source.sourceTitle || source.sourceDocumentName || 'Reference'}
                    </li>
                  );
                })}</ul>
              </details>
            )}
          </section>
        )}
      </section>
    </main>
  );
}

function App() {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);
  const [providers, setProviders] = useState({ google: false, github: false, loading: true });
  const [apiState, setApiState] = useState('checking');
  const [loadingProvider, setLoadingProvider] = useState('');
  const [notice, setNotice] = useState({ type: '', text: '' });
  const [verifiedUser, setVerifiedUser] = useState(null);
  const [sessionStatus, setSessionStatus] = useState('idle');
  const [sessionPath, setSessionPath] = useState('');
  const isAdminPath = currentPath === '/admin' || currentPath.startsWith('/admin/');

  const navigate = useCallback((path, replace = false) => {
    if (replace) window.history.replaceState({}, '', path);
    else window.history.pushState({}, '', path);
    setCurrentPath(path);
  }, []);

  useEffect(() => {
    const handlePopState = () => setCurrentPath(window.location.pathname);
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getOAuthProviders(controller.signal)
      .then((available) => setProviders({
        google: Boolean(available?.google),
        github: Boolean(available?.github),
        loading: false,
      }))
      .catch(() => setProviders({ google: false, github: false, loading: false, error: true }));
    fetch('/api/health', { signal: controller.signal })
      .then((response) => setApiState(response.ok ? 'up' : 'down'))
      .catch(() => setApiState('down'));
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (currentPath !== '/auth/callback') return undefined;
    const params = new URLSearchParams(window.location.hash.slice(1));
    const code = params.get('code');
    const failed = params.has('error');
    window.history.replaceState({}, '', '/');

    if (failed || !code) {
      clearSession();
      setCurrentPath('/');
      setNotice({ type: 'error', text: 'Sign-in could not be completed. Please try again.' });
      return undefined;
    }

    let active = true;
    exchangeOAuthCode(code)
      .then((session) => {
        if (!active || !session?.token || !session?.user) return;
        localStorage.setItem('aegiscloud_token', session.token);
        localStorage.setItem('aegiscloud_user', JSON.stringify(session.user));
        const destination = session.user.role === 'ADMIN' ? '/admin' : '/workspace';
        localStorage.setItem('aegiscloud_entry', destination);
        navigate(destination, true);
      })
      .catch((error) => {
        if (!active) return;
        clearSession();
        setCurrentPath('/');
        setNotice({ type: 'error', text: error.message || 'Sign-in could not be completed.' });
      });
    return () => { active = false; };
  }, [currentPath, navigate]);

  useEffect(() => {
    if (currentPath !== '/' || window.location.search) return;
    const token = localStorage.getItem('aegiscloud_token');
    const user = readStoredUser();
    if (token && user?.status === 1) {
      navigate(user.role === 'ADMIN' ? '/admin' : '/workspace', true);
    }
  }, [currentPath, navigate]);

  useEffect(() => {
    if (!isAdminPath && currentPath !== '/workspace') {
      setSessionStatus('idle');
      setVerifiedUser(null);
      return undefined;
    }

    const token = localStorage.getItem('aegiscloud_token');
    if (!token) {
      clearSession();
      navigate('/', true);
      setNotice({ type: 'error', text: 'Please sign in with an active workspace account.' });
      return undefined;
    }

    const controller = new AbortController();
    setSessionPath(currentPath);
    setSessionStatus('checking');
    setVerifiedUser(null);
    fetch('/api/users/me', {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then(async (response) => {
        const payload = await response.json().catch(() => null);
        if (!response.ok || payload?.code === 0 || !payload?.data) {
          throw new Error(payload?.message || 'Your session has expired. Please sign in again.');
        }
        const account = payload.data;
        if (account.status !== 1 || !['ADMIN', 'USER'].includes(account.role)) {
          throw new Error('This account no longer has active workspace access.');
        }
        localStorage.setItem('aegiscloud_user', JSON.stringify(account));
        setVerifiedUser(account);
        setSessionStatus('ready');
        if (account.role !== 'ADMIN' && isAdminPath) {
          navigate('/workspace', true);
        } else if (account.role === 'ADMIN' && !isAdminPath) {
          navigate('/admin', true);
        }
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        clearSession();
        setSessionStatus('invalid');
        navigate('/', true);
        setNotice({ type: 'error', text: error.message || 'Please sign in with an active workspace account.' });
      });

    return () => controller.abort();
  }, [currentPath, isAdminPath, navigate]);

  const hasAdminAccess = isAdminPath
    && sessionPath === currentPath
    && sessionStatus === 'ready'
    && verifiedUser?.role === 'ADMIN';
  const hasWorkspaceAccess = currentPath === '/workspace'
    && sessionPath === currentPath
    && sessionStatus === 'ready'
    && verifiedUser?.role === 'USER';

  const startSignIn = (provider) => {
    if (!providers[provider] || loadingProvider) return;
    setNotice({ type: '', text: '' });
    setLoadingProvider(provider);
    window.location.assign(`/oauth2/authorization/${provider}`);
  };

  const signOut = async () => {
    const token = localStorage.getItem('aegiscloud_token');
    try {
      await fetch('/api/users/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
    } catch {
      // Always clear the browser token, even when the backend is unreachable.
    } finally {
      clearSession();
      navigate('/', true);
    }
  };

  if (isAdminPath && hasAdminAccess) return <AdminOperationsCenter />;
  if (currentPath === '/workspace' && hasWorkspaceAccess) return <UserWorkspace onSignOut={signOut} />;
  if (currentPath === '/auth/callback' || isAdminPath || currentPath === '/workspace') {
    return <main className="callbackShell"><div className="callbackSpinner" /><p>Securing your CloudOps session...</p></main>;
  }

  return (
    <main className="pageShell">
      <section className="brandPanel" aria-label="CloudOps identity">
        <div className="brandLockup">
          <div className="brandMark"><Shield size={60} strokeWidth={1.9} /><span>A</span></div>
          <div><h1>CloudOps</h1><p>Knowledge Base &amp; Ticket Assistant</p></div>
        </div>
        <div className="goldLine" />
        <p className="brandCopy">Securely access internal knowledge, ask CloudOps questions, and get answers grounded in trusted sources.</p>
        <ul className="brandBenefits">
          <li><Check size={15} />Internal knowledge first</li>
          <li><Check size={15} />Web search when evidence is needed</li>
          <li><Check size={15} />Answers grounded in trusted sources</li>
        </ul>
        <p className="copyright">CloudOps Knowledge Base &amp; Ticket Assistant</p>
      </section>

      <section className="authColumn" aria-label="Authentication">
        <div className="authCard oauthCard">
          <div className="authHeader">
            <h2>Sign in to CloudOps</h2>
            <p>Continue with a trusted account to access your workspace.</p>
          </div>
          {notice.text && <div className={`notice ${notice.type}`}>{notice.text}</div>}
          <div className="oauthButtons">
            <button
              className="oauthButton"
              type="button"
              onClick={() => startSignIn('github')}
              disabled={providers.loading || !providers.github || Boolean(loadingProvider)}
              title={providers.github ? 'Continue with GitHub' : providers.error ? 'Authentication service is unavailable' : 'GitHub sign-in is not configured'}
            >
              <Github size={20} />
              <span>{loadingProvider === 'github' ? 'Connecting to GitHub...' : 'Continue with GitHub'}</span>
              {!providers.github && !providers.loading && <small>{providers.error ? 'Unavailable' : 'Not configured'}</small>}
            </button>
            <button
              className="oauthButton"
              type="button"
              onClick={() => startSignIn('google')}
              disabled={providers.loading || !providers.google || Boolean(loadingProvider)}
              title={providers.google ? 'Continue with Google' : providers.error ? 'Authentication service is unavailable' : 'Google sign-in is not configured'}
            >
              <span className="googleMark" aria-hidden="true">G</span>
              <span>{loadingProvider === 'google' ? 'Connecting to Google...' : 'Continue with Google'}</span>
              {!providers.google && !providers.loading && <small>{providers.error ? 'Unavailable' : 'Not configured'}</small>}
            </button>
          </div>
          {!providers.loading && !providers.google && !providers.github && (
            <p className="providerSetupHint">
              {providers.error
                ? 'The authentication service could not be reached. Check that the backend is running.'
                : 'Sign-in providers need to be configured by the workspace administrator.'}
            </p>
          )}
          <div className="authDivider"><span /><p>SECURE WORKSPACE ACCESS</p><span /></div>
          <p className="authPrivacy"><Shield size={15} /> New accounts are created on first sign-in. Administrator access follows workspace policy.</p>
        </div>

        <div className="healthCard" aria-label="System status">
          <StatusItem icon={Code2} label="API" state={apiState} />
          <StatusItem icon={Database} label="Identity" state={providers.loading ? 'checking' : (providers.google || providers.github ? 'up' : 'down')} />
        </div>
        <p className="systemText">{apiState === 'up' ? 'Secure sign-in is ready' : apiState === 'down' ? 'The API is currently unavailable' : 'Checking system status'}</p>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
