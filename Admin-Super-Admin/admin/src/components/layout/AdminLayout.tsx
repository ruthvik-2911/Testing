import * as React from "react"
import { Outlet, useNavigate, useLocation } from "react-router-dom"
import { Sidebar } from "./Sidebar"
import { ThemeProvider } from "../../context/ThemeContext"

export default function AdminLayout() {
  const [isOpen, setIsOpen] = React.useState(true)
  const navigate = useNavigate()

  React.useEffect(() => {
    const token = localStorage.getItem('admin_token')
    if (!token) {
      navigate('/admin/login')
    }
  }, [navigate])

  return (
    <ThemeProvider>
      <div className="flex">
        {/* Sidebar Wrapper */}
        <div 
          className={`hidden lg:block transition-all duration-300 ease-in-out ${
            isOpen ? "w-72" : "w-20"
          } flex-shrink-0`}
        >
           <Sidebar isOpen={isOpen} setIsOpen={setIsOpen} />
        </div>

        {/* Mobile Sidebar Overlay (Sidebar will handle its own visibility on mobile) */}
        <div className="lg:hidden">
          <Sidebar isOpen={isOpen} setIsOpen={setIsOpen} />
        </div>

        {/* Main Content */}
        <main className="flex-1 w-full overflow-x-hidden min-h-screen relative p-0 transition-all duration-300">
          <div className="max-w-[1600px] mx-auto">
             <Outlet />
          </div>
        </main>
      </div>
    </ThemeProvider>
  )
}
