import { useEffect, useState } from 'react'
import { Bell, Search, ChevronDown, User, Settings, LogOut, Menu, Shield, CheckCircle, Megaphone, Info, Wallet } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { getAuthSession, getRoleLabel, logoutSuperAdmin } from '../../lib/auth'
import { fetchAdminNotifications } from '../../lib/management'
import type { EmailNotificationRecord } from '../../lib/management'

interface TopNavbarProps {
  onMenuToggle: () => void
}

const formatRelativeTime = (timestamp: string) => {
  const date = new Date(timestamp);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return 'Just now';
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  return date.toLocaleDateString();
};

const getNotificationIcon = (type?: string) => {
  const t = type?.toLowerCase() || '';
  if (t.includes('registration')) return <User size={14} className="text-blue-500" />;
  if (t.includes('approval')) return <CheckCircle size={14} className="text-emerald-500" />;
  if (t.includes('account') || t.includes('security') || t.includes('login')) return <Shield size={14} className="text-amber-500" />;
  if (t.includes('ad') || t.includes('campaign')) return <Megaphone size={14} className="text-purple-500" />;
  if (t.includes('ticket') || t.includes('support')) return <Info size={14} className="text-rose-500" />;
  if (t.includes('payment') || t.includes('transaction') || t.includes('revenue')) return <Wallet size={14} className="text-emerald-600" />;
  if (t.includes('system') || t.includes('update')) return <Settings size={14} className="text-gray-500" />;

  return <Info size={14} className="text-primary-500" />;
};

const getPriorityStyles = (priority?: string) => {
  switch (priority?.toLowerCase()) {
    case 'high': return 'border-l-4 border-l-rose-500 bg-rose-50/10';
    case 'medium': return 'border-l-4 border-l-amber-500 bg-amber-50/10';
    default: return 'border-l-4 border-l-primary-500 bg-primary-50/10';
  }
};

export default function TopNavbar({ onMenuToggle }: TopNavbarProps) {
  const [notifications, setNotifications] = useState<EmailNotificationRecord[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifOpen, setNotifOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const navigate = useNavigate()
  const session = getAuthSession()

  useEffect(() => {
    const loadNotifs = async () => {
      try {
        const data = await fetchAdminNotifications()
        setNotifications(data || [])
        setUnreadCount(data?.length || 0)
      } catch (err) {
        console.error('Failed to fetch notifications:', err)
      }
    }
    loadNotifs()
    const interval = setInterval(loadNotifs, 60000)
    return () => clearInterval(interval)
  }, [])

  const handleLogout = async () => {
    await logoutSuperAdmin()
    setProfileOpen(false)
    navigate('/?reason=logged-out', { replace: true })
  }

  return (
    <header className="h-16 bg-white shadow-navbar flex items-center px-6 gap-4 sticky top-0 z-20">
      <button
        onClick={onMenuToggle}
        className="lg:hidden p-2 rounded-lg hover:bg-gray-100 text-gray-500 transition-colors"
      >
        <Menu size={20} />
      </button>

      <div className="flex-1 max-w-md relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text"
          placeholder="Search admins, ads, publishers..."
          className="w-full pl-9 pr-4 py-2 text-sm bg-gray-50 border border-gray-200 rounded-xl
                     focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100
                     transition-all duration-200 placeholder-gray-400"
        />
      </div>

      <div className="flex items-center gap-2 ml-auto">
        <div className="relative">
          <button
            onClick={() => {
              if (!notifOpen) setUnreadCount(0); // Mock mark as read
              setNotifOpen(!notifOpen);
              setProfileOpen(false)
            }}
            className="relative p-2 rounded-xl hover:bg-gray-100 transition-colors text-gray-600"
          >
            <Bell size={20} />
            {unreadCount > 0 && (
              <span className="absolute top-1 right-1 w-4 h-4 bg-rose-500 text-white text-[9px] font-bold
                                rounded-full flex items-center justify-center animate-pulse-soft">
                {unreadCount}
              </span>
            )}
          </button>

          {notifOpen && (
            <div className="absolute right-0 top-12 w-80 bg-white rounded-2xl shadow-card-hover border border-gray-100 z-50 animate-fade-in overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 bg-gray-50/50">
                <p className="font-bold text-gray-900 text-sm">Action Center</p>
                {unreadCount > 0 && <span className="badge-primary text-[10px] px-2 py-0.5 bg-rose-500 text-white border-none">{unreadCount} urgent</span>}
              </div>
              <div className="divide-y divide-gray-50 max-h-[400px] overflow-y-auto no-scrollbar">
                {notifications.length > 0 ? (
                  notifications.map((n) => (
                    <div key={n.id} className={`px-4 py-3 hover:bg-gray-50 transition-colors cursor-pointer ${getPriorityStyles(n.priority)}`}>
                      <div className="flex items-start gap-3">
                        <div className="p-2 bg-white rounded-lg shadow-sm border border-gray-50 mt-0.5">
                          {getNotificationIcon(n.type)}
                        </div>
                        <div className="flex-1">
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-[10px] font-black text-gray-400 uppercase tracking-widest">{n.type || 'Alert'}</p>
                            <p className="text-[9px] text-gray-400 font-medium">{formatRelativeTime(n.timestamp)}</p>
                          </div>
                          <p className="text-xs text-gray-900 font-bold mt-0.5">{n.trigger}</p>
                          <p className="text-[10px] text-gray-500 line-clamp-2 mt-0.5 font-medium leading-relaxed">{n.content}</p>
                        </div>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="px-4 py-12 text-center text-gray-500 flex flex-col items-center gap-2">
                    <div className="p-3 bg-gray-50 rounded-full text-gray-300">
                      <Bell size={24} />
                    </div>
                    <p className="text-xs font-bold">No pending actions</p>
                  </div>
                )}
              </div>
              <div className="px-4 py-3 border-t border-gray-100 text-center bg-gray-50/30">
                <button className="text-[10px] font-black uppercase tracking-widest text-primary-600 hover:text-primary-700 transition-colors">
                  Clear All Notifications
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="relative">
          <button
            onClick={() => { setProfileOpen(!profileOpen); setNotifOpen(false) }}
            className="flex items-center gap-2 pl-2 pr-3 py-1.5 rounded-xl hover:bg-gray-100 transition-colors"
          >
            <div className="w-8 h-8 bg-gradient-to-br from-primary-400 to-primary-600 rounded-full flex items-center justify-center">
              <span className="text-white font-semibold text-xs">SA</span>
            </div>
            <div className="text-left hidden sm:block">
              <p className="text-xs font-semibold text-gray-800">Super Admin</p>
              <p className="text-[10px] text-primary-600">{getRoleLabel(session?.role)}</p>
              <p className="text-[10px] text-gray-500">{session?.email ?? 'admin@keliri.com'}</p>
            </div>
            <ChevronDown size={14} className={`text-gray-500 transition-transform duration-200 ${profileOpen ? 'rotate-180' : ''}`} />
          </button>

          {profileOpen && (
            <div className="absolute right-0 top-12 w-48 bg-white rounded-2xl shadow-card-hover border border-gray-100 z-50 animate-fade-in overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-100">
                <p className="text-xs font-semibold text-gray-800">{getRoleLabel(session?.role)}</p>
                <p className="text-[10px] text-gray-400">{session?.email ?? 'admin@keliri.com'}</p>
              </div>
              <div className="py-1">
                <button
                  onClick={() => { navigate('/profile'); setProfileOpen(false) }}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                >
                  <User size={14} /> Profile
                </button>
                <button
                  onClick={() => { navigate('/settings'); setProfileOpen(false) }}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
                >
                  <Settings size={14} /> Settings
                </button>
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-red-500 hover:bg-red-50 transition-colors"
                >
                  <LogOut size={14} /> Sign Out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
