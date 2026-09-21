'use client';

import React, { useState, useEffect, useRef } from 'react';
import {
  Phone,
  Power,
  Sparkles,
  Smartphone,
  Volume2,
  Play,
  Pause,
  RefreshCw,
  CheckCircle2,
  ShieldCheck,
  Send,
  Radio,
  FileAudio,
} from 'lucide-react';
import CallSimulator from '@/components/CallSimulator';
import { AssistantConfig, DEFAULT_CONFIG } from '@/lib/types';

const QUICK_PRESETS = [
  { label: '💼 Ishdaman', text: 'men ishdaman ishdan chiqib qongiroq qilaman' },
  { label: '🚗 Ruldaman', text: 'men hozir rulda mashinadaman manzilga yetib borib aloqaga chiqaman' },
  { label: '👥 Majlisdaman', text: 'muhim yig\'ilishdaman majlis tugagach darhol o\'zim qo\'ng\'iroq qilaman' },
  { label: '🍽️ Tushlikdaman', text: 'hozir tushlik vaqti bo\'shab sizga qaytarib telefon qilaman' },
  { label: '✈️ Safardaman', text: 'hozir yo\'ldaman aloqaga chiqishim bilan siz bilan bog\'lanaman' },
];

export default function HomePage() {
  const [mounted, setMounted] = useState(false);
  const [config, setConfig] = useState<AssistantConfig>(DEFAULT_CONFIG);
  const [inputText, setInputText] = useState(DEFAULT_CONFIG.customMessage);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [isSimulatorOpen, setIsSimulatorOpen] = useState(false);
  const [, setSaveSuccess] = useState(false);

  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    setMounted(true);
    fetch('/api/assistant/config')
      .then((res) => res.json())
      .then((data) => {
        if (data && typeof data === 'object') {
          setConfig((prev) => ({ ...prev, ...data }));
          if (data.customMessage) {
            setInputText(data.customMessage);
          }
        }
      })
      .catch((err) => console.log('Config yuklanmadi, default ishlatilmoqda:', err));
  }, []);

  // Sozlamalarni serverga saqlash
  const saveConfig = async (newConfig: Partial<AssistantConfig>) => {
    const updated = { ...config, ...newConfig };
    setConfig(updated);
    try {
      await fetch('/api/assistant/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updated),
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch (e) {
      console.error('Saqlashda xato:', e);
    }
  };

  // OpenAI bilan yangi nutq va audio generatsiya qilish
  const handleGenerateAiSpeech = async () => {
    setIsGenerating(true);
    try {
      const res = await fetch('/api/assistant/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customMessage: inputText,
          voice: config.voice,
          ownerName: 'Zayniddin',
        }),
      });

      const data = await res.json();
      if (data.success) {
        saveConfig({
          customMessage: inputText,
          formattedSpeech: data.formattedSpeech,
          audioBase64: data.audioBase64,
        });

        if (audioRef.current) {
          audioRef.current.pause();
          audioRef.current = null;
        }
        setIsPlayingAudio(false);
      } else {
        alert(data.error || 'Generatsiya xatosi');
      }
    } catch (err: any) {
      alert('OpenAI bilan bog\'lanishda xato: ' + err.message);
    } finally {
      setIsGenerating(false);
    }
  };

  // Audio eshitib ko'rish
  const togglePlayAudio = () => {
    if (!config.audioBase64) {
      handleGenerateAiSpeech();
      return;
    }

    if (isPlayingAudio) {
      if (audioRef.current) {
        audioRef.current.pause();
      }
      setIsPlayingAudio(false);
    } else {
      if (!audioRef.current || audioRef.current.src !== config.audioBase64) {
        const audio = new Audio(config.audioBase64);
        audio.onended = () => setIsPlayingAudio(false);
        audioRef.current = audio;
      }
      audioRef.current
        .play()
        .then(() => setIsPlayingAudio(true))
        .catch((e) => console.log('Audio ijrosida to\'siq:', e));
    }
  };

  // Hydration mismatch oldini olish uchun
  if (!mounted) {
    return (
      <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4 text-white">
        <div className="w-12 h-12 rounded-2xl bg-indigo-600 animate-pulse flex items-center justify-center mb-4">
          <Sparkles size={24} className="text-white" />
        </div>
        <p className="text-sm font-medium text-slate-300">Zayniddin AI Call Assistant yuklanmoqda...</p>
      </div>
    );
  }

  return (
    <main className="min-h-screen py-8 px-4 sm:px-6 lg:px-8 max-w-4xl mx-auto">
      {/* Yuqori Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 mb-8 border-b border-slate-200">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-white shadow-lg shadow-indigo-500/20">
            <Sparkles size={26} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
                Zayniddin AI Call Assistant
              </h1>
              <span className="px-2.5 py-0.5 text-[11px] font-semibold tracking-wide uppercase rounded-full bg-indigo-100 text-indigo-700">
                Pro v1.0
              </span>
            </div>
            <p className="text-sm text-slate-500">
              OpenAI bilan 2 ta SIM kartani avtomatik boshqaruvchi shaxsiy telefon kotibi
            </p>
          </div>
        </div>

        {/* Live Simulator button */}
        <button
          onClick={() => setIsSimulatorOpen(true)}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl font-semibold text-sm bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-md shadow-emerald-600/20 active:scale-95 transition"
        >
          <Phone size={17} />
          <span>Qo&apos;ng&apos;iroqni Sinab Ko&apos;rish</span>
        </button>
      </div>

      {/* ASOSIY YOQISH / O'CHIRISH KARTASI */}
      <div
        className={`rounded-3xl p-6 sm:p-8 mb-8 border transition-all duration-300 ${
          config.isEnabled
            ? 'bg-gradient-to-br from-indigo-900 via-indigo-950 to-slate-900 border-indigo-700/50 text-white shadow-xl shadow-indigo-950/20'
            : 'bg-white border-slate-200 text-slate-900 shadow-sm'
        }`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2.5">
              <span
                className={`w-3 h-3 rounded-full ${
                  config.isEnabled ? 'bg-emerald-400 animate-ping' : 'bg-slate-400'
                }`}
              />
              <span
                className={`text-xs font-bold uppercase tracking-wider ${
                  config.isEnabled ? 'text-emerald-400' : 'text-slate-500'
                }`}
              >
                {config.isEnabled ? 'Xizmat Faol Holatda' : 'Xizmat O\'chirilgan'}
              </span>
            </div>
            <h2 className="text-2xl font-bold tracking-tight">
              {config.isEnabled
                ? 'AI Yordamchi qo\'ng\'iroqlarni qabul qilishga tayyor'
                : 'AI Yordamchi xizmatchi o\'chiq'}
            </h2>
            <p className={`text-sm ${config.isEnabled ? 'text-indigo-200' : 'text-slate-500'}`}>
              Yoqilganda telefoningizga tushgan qo&apos;ng&apos;iroq avtomatik ko&apos;tariladi, OpenAI nutqi aytiladi va trubka qo&apos;yiladi.
            </p>
          </div>

          <button
            onClick={() => saveConfig({ isEnabled: !config.isEnabled })}
            className={`flex items-center justify-center gap-3 px-6 py-3.5 rounded-2xl font-bold text-base transition-all active:scale-95 shadow-lg ${
              config.isEnabled
                ? 'bg-emerald-500 hover:bg-emerald-400 text-white shadow-emerald-500/25'
                : 'bg-slate-900 hover:bg-slate-800 text-white shadow-slate-900/10'
            }`}
          >
            <Power size={20} />
            <span>{config.isEnabled ? 'Yordamchi: YOQILGAN' : 'Yordamchini YOQISH'}</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* CHAP / ASOSIY SOZLAMALAR (2 ustun) */}
        <div className="md:col-span-2 space-y-6">
          {/* 1. SIM KARTALARNI TANLASH */}
          <div className="glass-card rounded-3xl p-6 shadow-sm">
            <div className="flex items-center gap-2 text-slate-900 font-bold text-lg mb-1">
              <Smartphone size={20} className="text-indigo-600" />
              <h3>SIM Karta Boshqaruvi (Dual SIM)</h3>
            </div>
            <p className="text-xs text-slate-500 mb-5">
              Qaysi SIM kartaga kelgan qo&apos;ng&apos;iroqlarga AI kotib javob berishini belgilang:
            </p>

            <div className="grid grid-cols-3 gap-3">
              {/* SIM 1 */}
              <button
                onClick={() => saveConfig({ selectedSim: 'sim1' })}
                className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition ${
                  config.selectedSim === 'sim1'
                    ? 'border-indigo-600 bg-indigo-50/70 text-indigo-950 font-bold ring-2 ring-indigo-500/20'
                    : 'border-slate-200 hover:border-slate-300 text-slate-700 bg-white'
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-xl flex items-center justify-center ${
                    config.selectedSim === 'sim1' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  <Radio size={18} />
                </div>
                <div className="text-center">
                  <div className="text-sm font-semibold">SIM 1</div>
                  <div className="text-[11px] text-slate-500">Asosiy SIM</div>
                </div>
              </button>

              {/* SIM 2 */}
              <button
                onClick={() => saveConfig({ selectedSim: 'sim2' })}
                className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition ${
                  config.selectedSim === 'sim2'
                    ? 'border-indigo-600 bg-indigo-50/70 text-indigo-950 font-bold ring-2 ring-indigo-500/20'
                    : 'border-slate-200 hover:border-slate-300 text-slate-700 bg-white'
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-xl flex items-center justify-center ${
                    config.selectedSim === 'sim2' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  <Radio size={18} />
                </div>
                <div className="text-center">
                  <div className="text-sm font-semibold">SIM 2</div>
                  <div className="text-[11px] text-slate-500">Ikkinchi SIM</div>
                </div>
              </button>

              {/* IKKALA SIM */}
              <button
                onClick={() => saveConfig({ selectedSim: 'both' })}
                className={`p-4 rounded-2xl border flex flex-col items-center gap-2 transition ${
                  config.selectedSim === 'both'
                    ? 'border-indigo-600 bg-indigo-50/70 text-indigo-950 font-bold ring-2 ring-indigo-500/20'
                    : 'border-slate-200 hover:border-slate-300 text-slate-700 bg-white'
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-xl flex items-center justify-center ${
                    config.selectedSim === 'both' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  <Sparkles size={18} />
                </div>
                <div className="text-center">
                  <div className="text-sm font-semibold">Ikkala SIM</div>
                  <div className="text-[11px] text-slate-500">SIM 1 + SIM 2</div>
                </div>
              </button>
            </div>
          </div>

          {/* 2. XABARNI YOZISH VA OPENAI GENERATOR */}
          <div className="glass-card rounded-3xl p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-lg">
                <Send size={20} className="text-indigo-600" />
                <h3>Holatingiz haqidagi xabar</h3>
              </div>
              <span className="text-xs text-indigo-600 font-semibold bg-indigo-50 px-2.5 py-1 rounded-lg">
                OpenAI GPT-4o-mini
              </span>
            </div>

            <p className="text-xs text-slate-500">
              Hozir nima ish bilan bandsiz? Pastga qisqacha yozing, AI uni chiroyli rasmiy kotib nutqiga aylantirib ovoz chiqaradi:
            </p>

            {/* Tezkor tugmalar */}
            <div className="flex flex-wrap gap-2 pt-1">
              {QUICK_PRESETS.map((preset, idx) => (
                <button
                  key={idx}
                  onClick={() => setInputText(preset.text)}
                  className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium transition active:scale-95"
                >
                  {preset.label}
                </button>
              ))}
            </div>

            <div className="relative">
              <textarea
                rows={3}
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                placeholder="Masalan: men ishdaman ishdan chiqib qongiroq qilaman..."
                className="w-full rounded-2xl border border-slate-200 p-4 text-slate-800 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600 resize-none transition bg-white"
              />
            </div>

            {/* AI Ovozini generatsiya qilish tugmasi */}
            <div className="flex items-center justify-between pt-2">
              <div className="text-xs text-slate-500">
                Ovoz: <span className="font-semibold text-slate-800 capitalize">{config.voice} (Tabiiy)</span>
              </div>

              <button
                onClick={handleGenerateAiSpeech}
                disabled={isGenerating || !inputText.trim()}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold text-sm bg-indigo-600 hover:bg-indigo-700 active:scale-95 text-white shadow-md shadow-indigo-600/20 disabled:opacity-50 transition"
              >
                {isGenerating ? (
                  <>
                    <RefreshCw size={16} className="animate-spin" />
                    <span>AI Ovoz Yaratmoqda...</span>
                  </>
                ) : (
                  <>
                    <Sparkles size={16} />
                    <span>AI Ovozini Generatsiya Qilish</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* O'NG USTUN: AI KOTIB NUTQI VA AUDIO PLAYER (1 ustun) */}
        <div className="space-y-6">
          <div className="glass-card rounded-3xl p-6 shadow-sm space-y-4">
            <div className="flex items-center gap-2 text-slate-900 font-bold text-lg">
              <Volume2 size={20} className="text-indigo-600" />
              <h3>Tayyor AI Kotib Nutqi</h3>
            </div>

            {/* Shakllantirilgan rasmiy matn */}
            <div className="p-4 rounded-2xl bg-indigo-50/70 border border-indigo-100 text-xs text-indigo-950 leading-relaxed italic">
              &ldquo;{config.formattedSpeech}&rdquo;
            </div>

            {/* Audio eshitib ko'rish */}
            <div className="p-4 rounded-2xl bg-slate-900 text-white space-y-3 shadow-md">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span className="flex items-center gap-1.5">
                  <FileAudio size={14} className="text-emerald-400" />
                  OpenAI TTS Audio (.mp3)
                </span>
                <span className="text-[10px] bg-slate-800 px-2 py-0.5 rounded text-emerald-400 font-semibold">
                  TAYYOR
                </span>
              </div>

              <button
                onClick={togglePlayAudio}
                className="w-full flex items-center justify-center gap-2.5 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm transition active:scale-95 shadow"
              >
                {isPlayingAudio ? (
                  <>
                    <Pause size={18} />
                    <span>To&apos;xtatish</span>
                  </>
                ) : (
                  <>
                    <Play size={18} />
                    <span>Ovozni Eshitib Ko&apos;rish</span>
                  </>
                )}
              </button>
            </div>

            <div className="text-[11px] text-slate-500 leading-normal border-t border-slate-100 pt-3">
              🛡️ Kimdir telefon qilganda, tizim avtomatik tarzda ushbu professional ovozni unga o&apos;qib beradi va gap tugashi bilan aloqani uzadi.
            </div>
          </div>

          {/* Xavfsizlik va Ma'lumot kartasi */}
          <div className="glass-card rounded-3xl p-6 shadow-sm space-y-3">
            <div className="flex items-center gap-2 text-slate-900 font-bold text-sm">
              <ShieldCheck size={18} className="text-emerald-600" />
              <h4>Zayniddin Profil &amp; Xavfsizlik</h4>
            </div>
            <ul className="text-xs text-slate-600 space-y-2">
              <li className="flex items-center gap-2">
                <CheckCircle2 size={14} className="text-emerald-500" />
                <span>OpenAI API ulanishi faol</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 size={14} className="text-emerald-500" />
                <span>Vercel Serverless API tayyor</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 size={14} className="text-emerald-500" />
                <span>Android Dual-SIM servisi bilan mos</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Simulator Modal */}
      <CallSimulator
        isOpen={isSimulatorOpen}
        onClose={() => setIsSimulatorOpen(false)}
        speechText={config.formattedSpeech}
        audioBase64={config.audioBase64}
        selectedSim={config.selectedSim}
        sim1Name={config.sim1Name}
        sim2Name={config.sim2Name}
      />
    </main>
  );
}
