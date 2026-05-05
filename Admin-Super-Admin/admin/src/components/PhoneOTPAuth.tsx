import { useState, useRef, useEffect } from 'react';
import { Phone, ArrowRight, Timer, CheckCircle } from 'lucide-react';
import { motion } from 'framer-motion';
import { adminApi } from '../services/api';

interface PhoneOTPAuthProps {
  onSuccess: (userData: any) => void;
  onError: (error: string) => void;
}

export default function PhoneOTPAuth({ onSuccess, onError }: PhoneOTPAuthProps) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [isOTPSent, setIsOTPSent] = useState(false);
  const [isSendingOTP, setIsSendingOTP] = useState(false);
  const [isVerifyingOTP, setIsVerifyingOTP] = useState(false);
  const [resendTimer, setResendTimer] = useState(0);
  const [isOTPComplete, setIsOTPComplete] = useState(false);
  
  const otpInputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Initialize OTP input refs
  useEffect(() => {
    otpInputRefs.current = Array(6).fill(null);
  }, []);

  // Resend OTP timer
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (resendTimer > 0) {
      interval = setInterval(() => {
        setResendTimer(prev => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [resendTimer]);

  // Handle OTP input changes
  const handleOTPChange = (index: number, value: string) => {
    if (value.length > 1) return; // Only allow single digit
    
    const newOTP = [...otp];
    newOTP[index] = value;
    setOtp(newOTP);

    // Auto-focus next input
    if (value && index < 5) {
      otpInputRefs.current[index + 1]?.focus();
    }

    // Check if OTP is complete
    const completeOTP = newOTP.join('');
    setIsOTPComplete(completeOTP.length === 6);
  };

  // Handle OTP key events
  const handleOTPKeydown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      otpInputRefs.current[index - 1]?.focus();
    }
  };

  // Send OTP
  const handleSendOTP = async () => {
    if (!phoneNumber || phoneNumber.length < 10) {
      onError('Please enter a valid phone number');
      return;
    }

    setIsSendingOTP(true);
    try {
      const response = await adminApi.sendOtp(phoneNumber);
      
      if (response.success) {
        setIsOTPSent(true);
        setResendTimer(60); // 60 seconds timer
        onError(''); // Clear any previous errors
      } else {
        onError(response.message || 'Failed to send OTP');
      }
    } catch (error: any) {
      onError(error.response?.data?.message || 'Failed to send OTP');
    } finally {
      setIsSendingOTP(false);
    }
  };

  // Verify OTP
  const handleVerifyOTP = async () => {
    if (!isOTPSent || !isOTPComplete) {
      onError('Please enter the complete OTP');
      return;
    }

    setIsVerifyingOTP(true);
    try {
      const completeOTP = otp.join('');
      const response = await adminApi.verifyOtp(phoneNumber, completeOTP);
      
      if (response.success && response.token && response.user) {
        // Store token and user data
        localStorage.setItem('admin_token', response.token);
        localStorage.setItem('admin_user', JSON.stringify(response.user));
        
        onSuccess(response.user);
      } else {
        onError(response.message || 'Invalid OTP. Please try again.');
      }
    } catch (error: any) {
      onError(error.response?.data?.message || 'Failed to verify OTP');
    } finally {
      setIsVerifyingOTP(false);
    }
  };

  // Resend OTP
  const handleResendOTP = async () => {
    if (resendTimer > 0) return;
    
    setIsOTPSent(false);
    setOtp(['', '', '', '', '', '']);
    await handleSendOTP();
  };

  return (
    <div className="space-y-6">
      {/* Phone Number Input */}
      {!isOTPSent && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-4"
        >
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
              Mobile Number
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400">
                <Phone className="w-[18px] h-[18px]" />
              </div>
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value.replace(/\D/g, ''))}
                placeholder="Enter 10-digit mobile number"
                className="block w-full pl-10 px-4 py-3 bg-white dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-xl text-gray-900 dark:text-white focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-colors shadow-sm"
                maxLength={10}
              />
            </div>
          </div>

          <button
            onClick={handleSendOTP}
            disabled={isSendingOTP || phoneNumber.length < 10}
            className="w-full flex items-center justify-center gap-2 py-3.5 px-4 bg-brand-500 hover:bg-brand-600 text-white font-semibold rounded-xl shadow-lg shadow-brand-500/20 transition-all disabled:opacity-70 disabled:cursor-not-allowed"
          >
            {isSendingOTP ? (
              <>
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                Sending OTP...
              </>
            ) : (
              <>
                Send OTP
                <ArrowRight className="w-[18px] h-[18px]" />
              </>
            )}
          </button>
        </motion.div>
      )}

      {/* OTP Verification */}
      {isOTPSent && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-4"
        >
          <div className="text-center mb-4">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Enter the 6-digit OTP sent to +91{phoneNumber}
            </p>
          </div>

          {/* OTP Input Fields */}
          <div className="flex justify-center gap-2 mb-6">
            {otp.map((digit, index) => (
              <input
                key={index}
                ref={(el) => {
                  otpInputRefs.current[index] = el;
                }}
                type="text"
                value={digit}
                onChange={(e) => handleOTPChange(index, e.target.value)}
                onKeyDown={(e) => handleOTPKeydown(index, e)}
                className="w-12 h-12 text-center text-lg font-semibold bg-white dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-lg focus:ring-2 focus:ring-brand-500 focus:border-brand-500 transition-colors"
                maxLength={1}
                inputMode="numeric"
                pattern="[0-9]"
              />
            ))}
          </div>

          {/* Verify OTP Button */}
          <button
            onClick={handleVerifyOTP}
            disabled={isVerifyingOTP || !isOTPComplete}
            className="w-full flex items-center justify-center gap-2 py-3.5 px-4 bg-brand-500 hover:bg-brand-600 text-white font-semibold rounded-xl shadow-lg shadow-brand-500/20 transition-all disabled:opacity-70 disabled:cursor-not-allowed mb-3"
          >
            {isVerifyingOTP ? (
              <>
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                Verifying...
              </>
            ) : (
              <>
                <CheckCircle className="w-[18px] h-[18px]" />
                Verify OTP
              </>
            )}
          </button>

          {/* Resend OTP */}
          <div className="text-center">
            <button
              onClick={handleResendOTP}
              disabled={resendTimer > 0}
              className="text-sm text-brand-600 dark:text-brand-400 hover:text-brand-500 disabled:text-gray-400 disabled:cursor-not-allowed transition-colors"
            >
              {resendTimer > 0 ? (
                <span className="flex items-center justify-center gap-2">
                  <Timer className="w-4 h-4" />
                  Resend OTP in {resendTimer}s
                </span>
              ) : (
                'Resend OTP'
              )}
            </button>
          </div>

          {/* Change Number */}
          <div className="text-center">
            <button
              onClick={() => {
                setIsOTPSent(false);
                setOtp(['', '', '', '', '', '']);
                setPhoneNumber('');
                setResendTimer(0);
              }}
              className="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
            >
              Change phone number
            </button>
          </div>
        </motion.div>
      )}
    </div>
  );
}
