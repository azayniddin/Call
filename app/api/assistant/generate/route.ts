import { NextRequest, NextResponse } from 'next/server';
import { openai } from '@/lib/openai';

export async function POST(req: NextRequest) {
  try {
    const { customMessage, voice = 'nova', ownerName = 'Zayniddin' } = await req.json();

    if (!process.env.OPENAI_API_KEY) {
      return NextResponse.json(
        { error: 'OpenAI API kaliti topilmadi. Iltimos .env.local faylida tekshiring.' },
        { status: 500 }
      );
    }

    const promptMessage = customMessage?.trim() || 'men ishdaman, ishdan chiqib qo\'ng\'iroq qilaman';

    // 1. OpenAI GPT orqali xushmuomala kotib nutqini shakllantirish
    const completion = await openai.chat.completions.create({
      model: 'gpt-4o-mini',
      temperature: 0.7,
      max_tokens: 150,
      messages: [
        {
          role: 'system',
          content: `Siz ${ownerName}ning shaxsiy sun'iy intellekt telefon yordamchisisiz (kotibi). 
${ownerName} hozir telefonini ko'tara olmaydi. 
Foydalanuvchi qoldirgan sababga asoslanib, qo'ng'iroq qiluvchi odamga aytiladigan o'ta xushmuomala, ixcham va tushunarli o'zbekcha nutq matnini tayyorlang.

Qat'iy qoidalar:
1. Nutq: "Assalomu alaykum! Men ${ownerName}ning sun'iy intellekt yordamchisiman..." bilan boshlansin.
2. Sababni muloyim tushuntiring va "${ownerName} ishdan/bo'shab o'zlari sizga qo'ng'iroq qiladilar" deb ayting.
3. Yakunida: "Xayr, salomat bo'ling!" deb tugating.
4. Faqat kotib o'qiydigan matnni bering. Ortiqcha belgi, emoji yoki qo'shimcha so'z qo'shmang.`,
        },
        {
          role: 'user',
          content: `Foydalanuvchi qoldirgan sabab: "${promptMessage}"`,
        },
      ],
    });

    const generatedSpeech = completion.choices[0]?.message?.content?.trim() || 
      `Assalomu alaykum! Men ${ownerName}ning sun'iy intellekt yordamchisiman. ${ownerName} hozir ishda, ishdan chiqib o'zlari sizga qo'ng'iroq qiladilar. Xayr, salomat bo'ling!`;

    // 2. OpenAI TTS orqali audio yaratish
    const mp3Response = await openai.audio.speech.create({
      model: 'tts-1',
      voice: voice as any,
      input: generatedSpeech,
    });

    const buffer = Buffer.from(await mp3Response.arrayBuffer());
    const audioBase64 = `data:audio/mp3;base64,${buffer.toString('base64')}`;

    return NextResponse.json({
      success: true,
      formattedSpeech: generatedSpeech,
      audioBase64: audioBase64,
    });
  } catch (error: any) {
    console.error('API generate xatosi:', error);
    return NextResponse.json(
      { error: error?.message || 'AI ovozini yaratishda xatolik yuz berdi' },
      { status: 500 }
    );
  }
}
