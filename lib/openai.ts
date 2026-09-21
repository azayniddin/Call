import OpenAI from 'openai';

export const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY || '',
});

export interface AssistantConfig {
  isEnabled: boolean;
  selectedSim: 'sim1' | 'sim2' | 'both';
  sim1Name: string;
  sim2Name: string;
  customMessage: string;
  formattedSpeech: string;
  voice: 'alloy' | 'echo' | 'fable' | 'onyx' | 'nova' | 'shimmer';
  audioBase64?: string;
  updatedAt: string;
}

export const DEFAULT_CONFIG: AssistantConfig = {
  isEnabled: true,
  selectedSim: 'both',
  sim1Name: 'SIM 1 (Asosiy)',
  sim2Name: 'SIM 2 (Ish)',
  customMessage: 'men ishdaman ishdan chiqib qongiroq qilaman',
  formattedSpeech: 'Assalomu alaykum! Men Zayniddinning sun\'iy intellekt yordamchisiman. Zayniddin hozir ishda, ishdan chiqib o\'zlari sizga qo\'ng\'iroq qiladilar. Xayr, salomat bo\'ling!',
  voice: 'nova',
  updatedAt: '2026-09-21T10:00:00.000Z',
};
