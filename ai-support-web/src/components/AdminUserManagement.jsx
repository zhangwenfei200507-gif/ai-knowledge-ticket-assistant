import React, { useCallback, useEffect, useState } from 'react';
import {
  BadgeCheck,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  KeyRound,
  RefreshCw,
  Search,
  ShieldCheck,
  UserRound,
  UsersRound,
  X,
} from 'lucide-react';
import { getAdminUser, getAdminUsers, updateAdminUserAccess } from '../api/admin';

const PAGE_SIZE = 20;

const formatDate = (value) => {
  if (!value) return 'Never recorded';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Never recorded'
    : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
};

function AdminUserManagement() {
  const currentUser = (() => {
    try {
      return JSON.parse(localStorage.getItem('aegiscloud_user') || '{}');
    } catch {
      return {};
    }
  })();
  const [users, setUsers] = useState([]);
  const [selected, setSelected] = useState(null);
  const [page, setPage] = useState(1);
  const [pageInfo, setPageInfo] = useState({ page: 1, pageCount: 1, total: 0 });
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ role: 'USER', status: 1 });
  const [confirmOpen, setConfirmOpen] = useState(false);

  const loadUsers = useCallback(async (targetPage = page) => {
    setLoading(true);
    setError('');
    try {
      const result = await getAdminUsers({
        page: targetPage,
        size: PAGE_SIZE,
        keyword,
        role,
        status,
      });
      const nextUsers = result?.items ?? [];
      setUsers(nextUsers);
      setPageInfo({
        page: result?.page ?? targetPage,
        pageCount: result?.pageCount ?? 1,
        total: result?.total ?? 0,
      });
      setPage(result?.page ?? targetPage);
      setSelected((current) => current ? nextUsers.find((item) => item.id === current.id) ?? current : null);
    } catch (requestError) {
      setUsers([]);
      setError(requestError.message || 'Unable to load user accounts.');
    } finally {
      setLoading(false);
    }
  }, [keyword, page, role, status]);

  useEffect(() => {
    loadUsers(page);
  }, [loadUsers, page]);

  const selectUser = async (user) => {
    setSelected(user);
    setEditing(false);
    setNotice('');
    setDetailLoading(true);
    try {
      const detail = await getAdminUser(user.id);
      setSelected(detail);
      setDraft({ role: detail.role, status: detail.status });
    } catch (requestError) {
      setError(requestError.message || 'Unable to load account details.');
    } finally {
      setDetailLoading(false);
    }
  };

  const applySearch = (event) => {
    event.preventDefault();
    setKeyword(searchInput.trim());
    setPage(1);
    setSelected(null);
  };

  const saveAccess = async () => {
    if (!selected) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const updated = await updateAdminUserAccess(selected.id, draft);
      setSelected(updated);
      setUsers((items) => items.map((item) => item.id === updated.id ? updated : item));
      setEditing(false);
      setConfirmOpen(false);
      setNotice('Account access updated. Existing sessions for this account have been revoked.');
      await loadUsers(page);
    } catch (requestError) {
      setError(requestError.message || 'Unable to update account access.');
      setConfirmOpen(false);
    } finally {
      setSaving(false);
    }
  };

  const beginEditing = () => {
    setDraft({
      role: selected.bootstrapAdmin ? 'ADMIN' : selected.role,
      status: selected.bootstrapAdmin ? 1 : selected.status,
    });
    setEditing(true);
    setNotice('');
    setError('');
  };

  const isSelf = selected && Number(selected.id) === Number(currentUser.id);
  const accessChanged = selected && (draft.role !== selected.role || draft.status !== selected.status);

  return (
    <section className="adminPageSurface userManagementPage">
      <div className="userManagementHeader">
        <div className="userManagementTitle">
          <span className="userManagementIcon"><UsersRound size={20} /></span>
          <div>
            <p>IDENTITY & ACCESS</p>
            <h2>Registered users</h2>
            <span>Manage OAuth accounts, administrator roles, and workspace access.</span>
          </div>
        </div>
        <div className="userCount"><strong>{pageInfo.total.toLocaleString()}</strong><span>matching accounts</span></div>
      </div>

      <div className="userManagementToolbar">
        <form className="userSearch" onSubmit={applySearch}>
          <Search size={17} />
          <input value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Search name, username, or email" aria-label="Search users" />
          <button type="submit">Search</button>
        </form>
        <label className="userFilter">
          <span>Role</span>
          <select value={role} onChange={(event) => { setRole(event.target.value); setPage(1); setSelected(null); }}>
            <option value="">All roles</option>
            <option value="ADMIN">Admin</option>
            <option value="USER">User</option>
          </select>
        </label>
        <label className="userFilter">
          <span>Access</span>
          <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); setSelected(null); }}>
            <option value="">All accounts</option>
            <option value="1">Active</option>
            <option value="0">Disabled</option>
          </select>
        </label>
        <button className="userRefresh" type="button" onClick={() => loadUsers(page)} disabled={loading} aria-label="Refresh users" title="Refresh users">
          <RefreshCw size={16} className={loading ? 'userSpin' : ''} />
        </button>
      </div>

      {(error || notice) && (
        <div className={`userFeedback ${error ? 'error' : 'success'}`} role="status">
          {error ? <CircleAlert size={16} /> : <BadgeCheck size={16} />}{error || notice}
          {error && <button type="button" onClick={() => loadUsers(page)}>Retry</button>}
        </div>
      )}

      <div className="userManagementLayout">
        <div className="userTableRegion">
          <div className="userTableScroll">
            <table className="userTable">
              <thead><tr><th>ACCOUNT</th><th>ROLE</th><th>ACCESS</th><th>PROVIDER</th><th>LAST SIGN-IN</th><th /></tr></thead>
              <tbody>
                {users.map((item) => (
                  <tr key={item.id} className={selected?.id === item.id ? 'selected' : ''} onClick={() => selectUser(item)}>
                    <td>
                      <span className="userIdentityCell"><i><UserRound size={16} /></i><span><strong>{item.displayName || item.username}</strong><small>{item.email}</small></span></span>
                    </td>
                    <td><span className={`userRoleTag ${item.role === 'ADMIN' ? 'admin' : ''}`}>{item.role === 'ADMIN' ? <ShieldCheck size={13} /> : null}{item.role}</span></td>
                    <td><span className={`userStatusTag ${item.status === 1 ? 'active' : 'disabled'}`}><i />{item.status === 1 ? 'Active' : 'Disabled'}</span></td>
                    <td className="userProviderCell">{item.linkedProviders ? item.linkedProviders.split(', ').map((provider) => <span key={provider}>{provider}</span>) : <span className="providerUnknown">None linked</span>}</td>
                    <td className="userDateCell">{formatDate(item.lastLoginTime)}</td>
                    <td><button className="userViewButton" type="button" onClick={(event) => { event.stopPropagation(); selectUser(item); }}>View</button></td>
                  </tr>
                ))}
                {!loading && users.length === 0 && (
                  <tr><td colSpan="6"><div className="userEmptyState"><UsersRound size={22} /><strong>No matching accounts</strong><span>New users appear after their first successful OAuth sign-in.</span></div></td></tr>
                )}
                {loading && users.length === 0 && (
                  <tr><td colSpan="6"><div className="userEmptyState"><RefreshCw size={20} className="userSpin" /><strong>Loading accounts</strong><span>Retrieving registered users from the backend.</span></div></td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="userPagination">
            <span>{pageInfo.total ? `Showing ${(pageInfo.page - 1) * PAGE_SIZE + 1}–${Math.min(pageInfo.page * PAGE_SIZE, pageInfo.total)} of ${pageInfo.total}` : 'No accounts to display'}</span>
            <div>
              <button type="button" aria-label="Previous page" disabled={pageInfo.page <= 1 || loading} onClick={() => setPage((current) => Math.max(1, current - 1))}><ChevronLeft size={17} /></button>
              <strong>{pageInfo.page} <i>/</i> {pageInfo.pageCount}</strong>
              <button type="button" aria-label="Next page" disabled={pageInfo.page >= pageInfo.pageCount || loading} onClick={() => setPage((current) => Math.min(pageInfo.pageCount, current + 1))}><ChevronRight size={17} /></button>
            </div>
          </div>
        </div>

        <aside className={`userDetailPanel ${selected ? 'hasSelection' : ''}`}>
          {!selected ? (
            <div className="userDetailEmpty"><span><UserRound size={22} /></span><strong>Account details</strong><p>Select an account to inspect its identity and manage access.</p></div>
          ) : detailLoading ? (
            <div className="userDetailEmpty"><RefreshCw size={20} className="userSpin" /><p>Loading account details…</p></div>
          ) : (
            <>
              <div className="userDetailHeader">
                <span className="userDetailAvatar"><UserRound size={22} /></span>
                <div><span>ACCOUNT PROFILE</span><h3>{selected.displayName || selected.username}</h3><p>{selected.email}</p></div>
                <button className="userDetailClose" type="button" onClick={() => { setSelected(null); setEditing(false); }} aria-label="Close account details"><X size={17} /></button>
              </div>
              <div className="userDetailBadges">
                <span className={`userRoleTag ${selected.role === 'ADMIN' ? 'admin' : ''}`}>{selected.role}</span>
                <span className={`userStatusTag ${selected.status === 1 ? 'active' : 'disabled'}`}><i />{selected.status === 1 ? 'Active' : 'Disabled'}</span>
                {selected.bootstrapAdmin && <span className="bootstrapBadge"><KeyRound size={12} />Protected admin</span>}
              </div>
              <dl className="userDetailFacts">
                <div><dt>Username</dt><dd>{selected.username}</dd></div>
                <div><dt>Sign-in providers</dt><dd>{selected.linkedProviders || 'No provider linked'}</dd></div>
                <div><dt>Last sign-in</dt><dd><Clock3 size={13} />{formatDate(selected.lastLoginTime)}</dd></div>
                <div><dt>Account created</dt><dd>{formatDate(selected.createdTime)}</dd></div>
                <div><dt>Last updated</dt><dd>{formatDate(selected.updatedTime)}</dd></div>
              </dl>

              {editing ? (
                <div className="userAccessEditor">
                  <label>Account role<select value={draft.role} disabled={selected.bootstrapAdmin} onChange={(event) => setDraft((current) => ({ ...current, role: event.target.value }))}><option value="USER">User</option><option value="ADMIN">Administrator</option></select></label>
                  <label>Account access<select value={draft.status} disabled={selected.bootstrapAdmin && selected.status === 1} onChange={(event) => setDraft((current) => ({ ...current, status: Number(event.target.value) }))}><option value={1}>Active</option>{(!selected.bootstrapAdmin || selected.status === 0) && <option value={0}>Disabled</option>}</select></label>
                  <div className="userEditorActions"><button type="button" onClick={() => setEditing(false)}>Cancel</button><button type="button" disabled={!accessChanged || saving || isSelf} onClick={() => setConfirmOpen(true)}>Review change</button></div>
                  {isSelf && <small>Your current account cannot be modified here.</small>}
                  {selected.bootstrapAdmin && <small>The configured administrator cannot be demoted or deactivated. Reactivation remains available if needed.</small>}
                </div>
              ) : (
                <div className="userDetailActions">
                  <button type="button" disabled={isSelf} onClick={beginEditing}><ShieldCheck size={15} />Manage access</button>
                  {isSelf && <small>Your current account is protected from self-modification.</small>}
                </div>
              )}
            </>
          )}
        </aside>
      </div>

      {confirmOpen && selected && (
        <div className="userConfirmScrim" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) setConfirmOpen(false); }}>
          <section className="userConfirmDialog" role="dialog" aria-modal="true" aria-labelledby="user-confirm-title">
            <button type="button" className="userConfirmClose" aria-label="Close confirmation" onClick={() => setConfirmOpen(false)} disabled={saving}><X size={17} /></button>
            <span className="userConfirmIcon"><ShieldCheck size={21} /></span>
            <h3 id="user-confirm-title">Confirm access change</h3>
            <p>Update <strong>{selected.email}</strong> to <strong>{draft.role}</strong> with <strong>{draft.status === 1 ? 'active' : 'disabled'}</strong> access? Their existing sessions will be revoked immediately.</p>
            <div><button type="button" onClick={() => setConfirmOpen(false)} disabled={saving}>Cancel</button><button type="button" onClick={saveAccess} disabled={saving}>{saving ? 'Saving…' : 'Confirm change'}</button></div>
          </section>
        </div>
      )}
    </section>
  );
}

export default AdminUserManagement;
