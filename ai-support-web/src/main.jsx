import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ArrowRight,
  Check,
  Code2,
  Database,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  Search,
  Shield,
  User,
} from 'lucide-react';
import { login, register, resetPassword } from './api/auth';
import AdminOperationsCenter from './AdminOperationsCenter';
import './styles.css';

const emptyForms = {
  signin: {
    usernameOrEmail: '',
    password: '',
    remember: true,
  },
  register: {
    email: '',
    password: '',
    confirmPassword: '',
    displayName: '',
  },
  forgot: {
    usernameOrEmail: '',
    registeredEmail: '',
    newPassword: '',
    confirmPassword: '',
  },
};

function getStoredUserRole() {
  try {
    return JSON.parse(localStorage.getItem('aegiscloud_user') || '{}')?.role;
  } catch {
    return undefined;
  }
}

function AuthInput({
  icon: Icon,
  rightIcon,
  label,
  type = 'text',
  value,
  onChange,
  placeholder,
  autoComplete,
  minLength,
  required = true,
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <div className="inputShell">
        <Icon className="fieldIcon" size={19} strokeWidth={1.8} />
        <input
          type={type}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          autoComplete={autoComplete}
          minLength={minLength}
          required={required}
        />
        {rightIcon}
      </div>
    </label>
  );
}

function StatusItem({ icon: Icon, label }) {
  return (
    <div className="statusItem">
      <div className="statusIcon">
        <Icon size={19} strokeWidth={1.7} />
      </div>
      <div>
        <strong>{label}</strong>
        <span>
          <i />
          Healthy
        </span>
      </div>
    </div>
  );
}

function App() {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);
  const [mode, setMode] = useState('signin');
  const [forms, setForms] = useState(emptyForms);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState({ type: '', text: '' });
  const hasAdminAccess = currentPath === '/admin'
    && Boolean(localStorage.getItem('aegiscloud_token'))
    && getStoredUserRole() === 'ADMIN';

  const copy = useMemo(() => {
    if (mode === 'register') {
      return {
        title: 'Register account',
        subtitle: 'Create your workspace account.',
        button: 'Create account',
      };
    }

    if (mode === 'forgot') {
      return {
        title: 'Reset password',
        subtitle: 'Verify your email and choose a new password.',
        button: 'Reset password',
      };
    }

    return {
      title: 'Sign in to AegisCloud',
      subtitle: 'Use your workspace account to continue.',
      button: 'Sign in',
    };
  }, [mode]);

  const updateForm = (formName, field, value) => {
    setForms((current) => ({
      ...current,
      [formName]: {
        ...current[formName],
        [field]: value,
      },
    }));
  };

  const switchMode = (nextMode) => {
    setMode(nextMode);
    setNotice({ type: '', text: '' });
    setShowPassword(false);
  };

  useEffect(() => {
    if (currentPath === '/admin' && !hasAdminAccess) {
      window.history.replaceState({}, '', '/');
      setCurrentPath('/');
      setNotice({ type: 'error', text: 'Admin access requires an administrator account.' });
    }
  }, [currentPath, hasAdminAccess]);

  const submit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setNotice({ type: '', text: '' });

    try {
      if (mode === 'signin') {
        const data = await login({
          usernameOrEmail: forms.signin.usernameOrEmail.trim(),
          password: forms.signin.password,
        });

        if (data?.token) {
          localStorage.setItem('aegiscloud_token', data.token);
          localStorage.setItem('aegiscloud_user', JSON.stringify(data.user ?? {}));
        }

        const userRole = data?.user?.role === 'ADMIN' ? 'ADMIN' : 'USER';
        localStorage.setItem('aegiscloud_entry', userRole === 'ADMIN' ? '/admin' : '/workspace');

        setNotice({
          type: 'success',
          text:
            userRole === 'ADMIN'
              ? 'Signed in as ADMIN. Admin console access is ready.'
              : 'Signed in as USER. Workspace access is ready.',
        });

        if (userRole === 'ADMIN') {
          window.history.pushState({}, '', '/admin');
          setCurrentPath('/admin');
        }
      }

      if (mode === 'register') {
        await register({
          email: forms.register.email.trim(),
          password: forms.register.password,
          confirmPassword: forms.register.confirmPassword,
          displayName: forms.register.displayName.trim() || undefined,
        });

        setNotice({ type: 'success', text: 'Account created. You can sign in now.' });
        setMode('signin');
        setForms((current) => ({
          ...current,
          signin: {
            ...current.signin,
            usernameOrEmail: current.register.email,
          },
        }));
      }

      if (mode === 'forgot') {
        await resetPassword({
          usernameOrEmail: forms.forgot.usernameOrEmail.trim(),
          registeredEmail: forms.forgot.registeredEmail.trim(),
          newPassword: forms.forgot.newPassword,
          confirmPassword: forms.forgot.confirmPassword,
        });

        setNotice({ type: 'success', text: 'Password updated. You can sign in now.' });
        setMode('signin');
      }
    } catch (error) {
      setNotice({
        type: 'error',
        text: error.message || 'The backend did not accept this request.',
      });
    } finally {
      setLoading(false);
    }
  };

  const passwordToggle = (
    <button className="iconButton" type="button" aria-label="Toggle password visibility" onClick={() => setShowPassword((value) => !value)}>
      {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
    </button>
  );

  if (currentPath === '/admin' && hasAdminAccess) {
    return <AdminOperationsCenter />;
  }

  return (
    <main className="pageShell">
      <section className="brandPanel" aria-label="AegisCloud identity">
        <div className="brandLockup">
          <div className="brandMark">
            <Shield size={60} strokeWidth={1.9} />
            <span>A</span>
          </div>
          <div>
            <h1>AegisCloud</h1>
            <p>CloudOps AI Knowledge Support</p>
          </div>
        </div>
        <div className="goldLine" />
        <p className="brandCopy">Securely access internal runbooks, ask CloudOps questions, and get grounded answers with confidence.</p>
        <p className="copyright">© 2026 AegisCloud Inc. All rights reserved.</p>
      </section>

      <section className="authColumn" aria-label="Authentication">
        <form className="authCard" onSubmit={submit}>
          <div className="authHeader">
            <h2>{copy.title}</h2>
            <p>{copy.subtitle}</p>
          </div>

          {mode === 'signin' && (
            <>
              <AuthInput
                icon={User}
                label="Username or email"
                value={forms.signin.usernameOrEmail}
                onChange={(value) => updateForm('signin', 'usernameOrEmail', value)}
                placeholder="you@company.com"
                autoComplete="username"
              />
              <AuthInput
                icon={LockKeyhole}
                rightIcon={passwordToggle}
                label="Password"
                type={showPassword ? 'text' : 'password'}
                value={forms.signin.password}
                onChange={(value) => updateForm('signin', 'password', value)}
                placeholder="Enter your password"
                autoComplete="current-password"
                minLength={6}
              />
              <div className="formRow">
                <button
                  className={`rememberButton ${forms.signin.remember ? 'active' : ''}`}
                  type="button"
                  onClick={() => updateForm('signin', 'remember', !forms.signin.remember)}
                >
                  <span>{forms.signin.remember && <Check size={13} strokeWidth={3} />}</span>
                  Remember me
                </button>
                <button className="textButton" type="button" onClick={() => switchMode('forgot')}>
                  Forgot password?
                </button>
              </div>
            </>
          )}

          {mode === 'register' && (
            <>
              <AuthInput
                icon={Mail}
                label="Email"
                type="email"
                value={forms.register.email}
                onChange={(value) => updateForm('register', 'email', value)}
                placeholder="you@company.com"
                autoComplete="email"
              />
              <AuthInput
                icon={User}
                label="Display name"
                value={forms.register.displayName}
                onChange={(value) => updateForm('register', 'displayName', value)}
                placeholder="Optional"
                autoComplete="name"
                required={false}
              />
              <AuthInput
                icon={LockKeyhole}
                rightIcon={passwordToggle}
                label="Password"
                type={showPassword ? 'text' : 'password'}
                value={forms.register.password}
                onChange={(value) => updateForm('register', 'password', value)}
                placeholder="At least 6 characters"
                autoComplete="new-password"
                minLength={6}
              />
              <AuthInput
                icon={LockKeyhole}
                label="Confirm password"
                type={showPassword ? 'text' : 'password'}
                value={forms.register.confirmPassword}
                onChange={(value) => updateForm('register', 'confirmPassword', value)}
                placeholder="Repeat password"
                autoComplete="new-password"
                minLength={6}
              />
            </>
          )}

          {mode === 'forgot' && (
            <>
              <AuthInput
                icon={User}
                label="Username or email"
                value={forms.forgot.usernameOrEmail}
                onChange={(value) => updateForm('forgot', 'usernameOrEmail', value)}
                placeholder="you@company.com"
                autoComplete="username"
              />
              <AuthInput
                icon={Mail}
                label="Registered email"
                type="email"
                value={forms.forgot.registeredEmail}
                onChange={(value) => updateForm('forgot', 'registeredEmail', value)}
                placeholder="you@company.com"
                autoComplete="email"
              />
              <AuthInput
                icon={LockKeyhole}
                rightIcon={passwordToggle}
                label="New password"
                type={showPassword ? 'text' : 'password'}
                value={forms.forgot.newPassword}
                onChange={(value) => updateForm('forgot', 'newPassword', value)}
                placeholder="At least 6 characters"
                autoComplete="new-password"
                minLength={6}
              />
              <AuthInput
                icon={LockKeyhole}
                label="Confirm password"
                type={showPassword ? 'text' : 'password'}
                value={forms.forgot.confirmPassword}
                onChange={(value) => updateForm('forgot', 'confirmPassword', value)}
                placeholder="Repeat password"
                autoComplete="new-password"
                minLength={6}
              />
            </>
          )}

          {notice.text && <div className={`notice ${notice.type}`}>{notice.text}</div>}

          <button className="primaryButton" type="submit" disabled={loading}>
            {loading ? 'Working...' : copy.button}
            <ArrowRight size={22} strokeWidth={1.8} />
          </button>

          {mode === 'signin' ? (
            <div className="registerBlock">
              <div className="divider">
                <span />
                <p>New to this workspace?</p>
                <span />
              </div>
              <button className="secondaryButton" type="button" onClick={() => switchMode('register')}>
                Register account
              </button>
            </div>
          ) : (
            <button className="backButton" type="button" onClick={() => switchMode('signin')}>
              Back to sign in
            </button>
          )}
        </form>

        <div className="healthCard" aria-label="System status">
          <StatusItem icon={Code2} label="API" />
          <StatusItem icon={Search} label="Search" />
          <StatusItem icon={Database} label="Storage" />
        </div>
        <p className="systemText">All systems operational</p>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
