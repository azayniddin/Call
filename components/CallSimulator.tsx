'use client';

import React, { useState, useEffect, useRef } from 'react';
import { PhoneCall, PhoneOff, Mic, Sparkles, Volume2, X } from 'lucide-react';

interface CallSimulatorProps {
  isOpen: boolean;
  onClose: () => void;
  speechText: string;
  audioBase64?: string;
  selectedSim: string;
  sim1Name: string;
  sim2Name: string;
}

export default function CallSimulator({
  isOpen,
  onClose,
  speechText,
  audioBase64,
  selectedSim,
  sim1Name,
  sim2Name,
}: CallSimulatorProps) {
  const [callState, setCallState] = useState<'ringing' | 'connected' | 'ended'>('ringing');
  const [callSeconds, setCallSeconds] = useState(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const activeSimName =
    selectedSim === 'sim1'
      ? sim1Name
      : selectedSim === 'sim2'
      ? sim2Name
      : `${sim1Name} (Avtomatik)`;

  useEffect(() => {
    if (!isOpen) {
      setCallState('ringing');
      setCallSeconds(0);
      if (audioRef.current) {
        try {
          audioRef.current.pause();
          audioRef.current.currentTime = 0;
        } catch (e) {}
      }
      return;
    }

    setCallState('ringing');
    setCallSeconds(0);

    const answerTimer = setTimeout(() => {
      setCallState('connected');

      if (typeof window !== 'undefined') {
        if (audioBase64) {
          try {
            const audio = new Audio(audioBase64);
            audioRef.current = audio;
            audio.play().catch((err) => console.log('Audio autoplay:', err));

            audio.onended = () => {
              setTimeout(() => {
                setCallState('ended');
              }, 800);
            };
          } catch (e) {
            console.error('Audio yaratishda xato:', e);
            setTimeout(() => setCallState('ended'), 3000);
          }
        } else if ('speechSynthesis' in window) {
          try {
            const utterance = new SpeechSynthesisUtterance(speechText);
            utterance.lang = 'uz-UZ';
            utterance.rate = 0.95;
            utterance.onend = () => {
              setTimeout(() => setCallState('ended'), 800);
            };
            window.speechSynthesis.speak(utterance);
          } catch (e) {
            setTimeout(() => setCallState('ended'), 3000);
          }
        } else {
          setTimeout(() => setCallState('ended'), 4000);
        }
      }
    }, 1800);

    return () => {
      clearTimeout(answerTimer);
      if (audioRef.current) {
        try {
          audioRef.current.pause();
        } catch (e) {}
      }
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        try {
          window.speechSynthesis.cancel();
        } catch (e) {}
      }
    };
  }, [isOpen, audioBase64, speechText]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (callState === 'connected') {
      interval = setInterval(() => {
        setCallSeconds((prev) => prev + 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [callState]);

  if (!isOpen) return null;

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60)
      .toString()
      .padStart(2, '0');
    const s = (sec % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md">
      <div className="relative w-full max-w-sm rounded-[42px] p-6 shadow-2xl border-4 border-slate-700 bg-slate-900 text-white overflow-hidden flex flex-col justify-between min-h-[580px]">
        {/* Yuqori panel va Close */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-full bg-slate-800 border border-slate-700 text-slate-300">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
            {activeSimName}
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-full bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white transition"
          >
            <X size={18} />
          </button>
        </div>

        {/* Markaziy qism: Qo'ng'iroq qiluvchi info */}
        <div className="flex flex-col items-center text-center my-auto space-y-4">
          <div className="relative">
            <div
              className={`w-28 h-28 rounded-full flex items-center justify-center shadow-inner border-2 transition-all duration-500 ${
                callState === 'ringing'
                  ? 'bg-indigo-600/30 border-indigo-500 animate-pulse-ring'
                  : callState === 'connected'
                  ? 'bg-emerald-600/30 border-emerald-500'
                  : 'bg-rose-600/20 border-rose-500'
              }`}
            >
              {callState === 'ringing' && <PhoneCall size={44} className="text-indigo-400 animate-bounce" />}
              {callState === 'connected' && <Sparkles size={46} className="text-emerald-400 animate-pulse" />}
              {callState === 'ended' && <PhoneOff size={44} className="text-rose-400" />}
            </div>
            {callState === 'connected' && (
              <span className="absolute -bottom-1 -right-1 p-1.5 rounded-full bg-indigo-600 text-white shadow-lg">
                <Mic size={16} />
              </span>
            )}
          </div>

          <div>
            <h3 className="text-2xl font-bold tracking-tight text-white">+998 (90) 123-45-67</h3>
            <p className="text-sm font-medium mt-1">
              {callState === 'ringing' && (
                <span className="text-amber-400 flex items-center justify-center gap-1.5">
                  Kiruvchi qo&apos;ng&apos;iroq... (Avto-javob kutilmoqda)
                </span>
              )}
              {callState === 'connected' && (
                <span className="text-emerald-400 flex items-center justify-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                  AI Yordamchi gapirmoqda: {formatTimer(callSeconds)}
                </span>
              )}
              {callState === 'ended' && (
                <span className="text-rose-400 font-semibold">Qo&apos;ng&apos;iroq avtomatik yakunlandi</span>
              )}
            </p>
          </div>

          {/* AI Gapirayotgan jonli matn qutisi */}
          <div className="w-full bg-slate-800/80 border border-slate-700/60 rounded-2xl p-4 text-xs text-left text-slate-200 leading-relaxed shadow-md">
            <div className="flex items-center gap-1.5 text-indigo-400 font-semibold mb-1.5 uppercase tracking-wider text-[10px]">
              <Volume2 size={13} /> OpenAI Ovozli Kotib Nutqi:
            </div>
            <p className="italic text-slate-300 font-normal">&ldquo;{speechText}&rdquo;</p>
          </div>
        </div>

        {/* Pastki tugmalar */}
        <div className="flex items-center justify-around pt-4 border-t border-slate-800">
          <div className="flex flex-col items-center">
            <button
              onClick={onClose}
              className="w-16 h-16 rounded-full bg-rose-600 hover:bg-rose-500 active:scale-95 flex items-center justify-center text-white shadow-lg transition"
            >
              <PhoneOff size={28} />
            </button>
            <span className="text-[11px] text-slate-400 mt-2 font-medium">
              {callState === 'ended' ? 'Yopish' : 'Uzish'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
