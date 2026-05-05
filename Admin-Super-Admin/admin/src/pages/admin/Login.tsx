import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { Mail, Lock, Phone, ArrowRight, AlertCircle } from "lucide-react";
import { motion } from "framer-motion";
import PhoneOTPAuth from "../../components/PhoneOTPAuth";
import { adminApi } from "../../services/api";

type TabType = "email" | "otp";

export default function AdminLogin() {
  const [activeTab, setActiveTab] = useState<TabType>("email");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { register, handleSubmit } = useForm();

  useEffect(() => {
    // Explicitly remove dark mode from root for login page
    document.documentElement.classList.remove("dark");
  }, []);

  const onSubmit = async (data: any) => {
    setIsSubmitting(true);
    setError(null);
    try {
      if (activeTab === "email") {
        console.log('Login Payload:', { email: data.email, password: data.password });
        
        // Use Backend API authentication
        try {
          console.log('Processing backend login for:', data.email);
          
          const response = await adminApi.login(data.email, data.password);
          
          console.log('Backend login response:', response);
          
          if (response.success && response.token && response.user) {
            // Store token and user data
            localStorage.setItem('admin_token', response.token);
            localStorage.setItem('admin_user', JSON.stringify(response.user));
            
            console.log('✅ Backend authentication successful:', response.user);
            
            // Navigate to dashboard
            navigate("/admin/dashboard");
          } else {
            setError(response.message || 'Login failed. Please check your credentials.');
          }
        } catch (error: any) {
          console.error('❌ Backend authentication failed:', error);
          
          // Check if it's a registration pending or not approved error
          if (error.response?.data?.message) {
            setError(error.response.data.message);
          } else if (error.response?.status === 401) {
            setError('Invalid credentials or account not approved yet.');
          } else if (error.response?.status === 403) {
            setError('Your account is pending approval. Please wait for admin approval.');
          } else {
            setError('Authentication failed. Please check your credentials.');
          }
        }
      } else {
        // OTP login is now handled by the PhoneOTPAuth component
        setError("Please use the OTP form below to authenticate with your phone number.");
      }
    } catch (err: any) {
      setError(err.message || "An unexpected error occurred. Please try again later.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen bg-gray-50 md:bg-white dark:bg-[#0E1117]">
      {/* Left Panel - Branding (Hidden on mobile) */}
      <div className="hidden lg:flex lg:w-1/2 relative bg-gradient-to-br from-brand-500 via-brand-600 to-brand-700 overflow-hidden text-white p-12 flex-col justify-between">
        {/* Abstract background shapes */}
        <div className="absolute top-[-10%] right-[-10%] w-96 h-96 bg-brand-400 rounded-full blur-[100px] opacity-60"></div>
        <div className="absolute bottom-[-10%] left-[-10%] w-[30rem] h-[30rem] bg-brand-800 rounded-full blur-[120px] opacity-60"></div>

        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center font-bold text-xl backdrop-blur-sm border border-white/30 shadow-lg">
              K
            </div>
            <div>
              <h1 className="font-bold text-lg leading-tight tracking-wide">KELIRI</h1>
              <p className="text-[10px] uppercase font-semibold text-brand-100 tracking-wider">Admin Panel</p>
            </div>
          </div>

          <div className="mt-24 max-w-xl">
            <h2 className="text-4xl md:text-5xl font-bold leading-tight mb-6 tracking-tight">
              Manage Your Advertising<br />Network From One<br />Unified Dashboard
            </h2>
            <p className="text-brand-100 text-lg md:text-xl font-medium leading-relaxed mb-12">
              Take complete control of your business advertising ecosystem — create campaigns, manage branch-level publishers, monitor performance, and track spending — all from a single, powerful admin platform.
            </p>

            <div className="grid grid-cols-2 gap-4">
              {[
                { icon: <Mail className="w-5 h-5" />, title: "Email Authentication", desc: "Secure Firebase email-based login system." },
                { icon: <Lock className="w-5 h-5" />, title: "Secure Login", desc: "Enterprise-grade security with Firebase authentication." },
                { icon: <Phone className="w-5 h-5" />, title: "Mobile Support", desc: "Phone OTP authentication for mobile access." },
                { icon: <ArrowRight className="w-5 h-5" />, title: "Quick Access", desc: "Fast and secure authentication process." },
              ].map((feature, i) => (
                <div key={i} className="bg-white/10 backdrop-blur-md border border-white/10 rounded-2xl p-4 hover:bg-white/15 transition-colors">
                  <div className="flex items-center gap-3 mb-2">
                    <div className="p-2 bg-white/10 rounded-lg shadow-sm">
                      {feature.icon}
                    </div>
                    <span className="font-semibold text-sm">{feature.title}</span>
                  </div>
                  <p className="text-xs text-brand-200 leading-relaxed">{feature.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="relative z-10 text-xs text-brand-200 font-medium tracking-wide">
          2026 KELIRI
        </div>
      </div>

      {/* Right Panel - Login Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 sm:p-12 lg:p-24 relative bg-gray-50 dark:bg-[#0E1117] transition-colors">
        <div className="w-full max-w-md">
          {/* Mobile Header (Hidden on desktop) */}
          <div className="flex lg:hidden items-center gap-3 mb-10">
            <div className="w-10 h-10 bg-brand-500 rounded-xl flex items-center justify-center font-bold text-xl text-white shadow-lg shadow-brand-500/30">
              K
            </div>
            <div>
              <h1 className="font-bold text-lg text-gray-900 dark:text-white leading-tight tracking-wide">KELIRI</h1>
              <p className="text-[10px] uppercase font-semibold text-brand-500 tracking-wider">Admin Panel</p>
            </div>
          </div>

          <div className="mb-10">
            <h2 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">Welcome back</h2>
            <p className="text-gray-500 dark:text-gray-400">Sign in to your Admin account</p>
          </div>

          <div className="bg-gray-200/60 dark:bg-[#1C1F26] p-1.5 rounded-xl flex mb-8">
            <button
              type="button"
              onClick={() => setActiveTab("email")}
              className={`flex-1 flex items-center justify-center gap-2 py-3 text-sm font-semibold rounded-lg transition-all duration-200 ${activeTab === "email"
                  ? "bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-sm border border-gray-200/50 dark:border-gray-700"
                  : "text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                }`}
            >
              <Mail className="w-4 h-4" />
              Email & Password
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("otp")}
              className={`flex-1 flex items-center justify-center gap-2 py-3 text-sm font-semibold rounded-lg transition-all duration-200 ${activeTab === "otp"
                  ? "bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-sm border border-gray-200/50 dark:border-gray-700"
                  : "text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                }`}
            >
              <Phone className="w-4 h-4" />
              Phone & OTP
            </button>
          </div>

          {error && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mb-6 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800/50 rounded-xl flex items-start gap-3 text-red-600 dark:text-red-400"
            >
              <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
              <p className="text-sm font-medium">{error}</p>
            </motion.div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            {activeTab === "email" ? (
              <motion.div
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2 }}
                className="space-y-5"
              >
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                    Email Address
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
                      <Mail className="w-[18px] h-[18px]" />
                    </div>
                    <input
                      {...register("email", { required: true })}
                      type="email"
                      placeholder="admin@keliri.com"
                      className="block w-full pl-10 px-4 py-3 bg-white dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-xl text-gray-900 dark:text-white focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-colors shadow-sm"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                    Password
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
                      <Lock className="w-[18px] h-[18px]" />
                    </div>
                    <input
                      {...register("password", { required: true })}
                      type="password"
                      placeholder="Enter your password"
                      className="block w-full pl-10 pr-10 px-4 py-3 bg-white dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-xl text-gray-900 dark:text-white focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-colors shadow-sm"
                    />
                  </div>
                </div>
              </motion.div>
            ) : (
              <motion.div
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2 }}
                className="space-y-5"
              >
                <PhoneOTPAuth 
                  onSuccess={(userData) => {
                    console.log('✅ OTP authentication successful:', userData);
                    navigate("/admin/dashboard");
                  }}
                  onError={(errorMessage) => {
                    setError(errorMessage);
                  }}
                />
              </motion.div>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full flex items-center justify-center gap-2 py-3.5 px-4 bg-brand-500 hover:bg-brand-600 text-white font-semibold rounded-xl shadow-lg shadow-brand-500/20 transition-all disabled:opacity-70 disabled:cursor-not-allowed mt-6"
            >
              {isSubmitting ? "Authenticating..." : "Sign In"}
              {!isSubmitting && <ArrowRight className="w-[18px] h-[18px]" />}
            </button>
          </form>

          <div className="mt-10 flex items-center justify-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
            Protected by <span className="font-semibold text-gray-700 dark:text-gray-300">KELIRI Security</span>
            <span>·</span>
            Role-based access control
          </div>
        </div>
      </div>
    </div>
  );
}
