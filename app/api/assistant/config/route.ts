import { NextRequest, NextResponse } from 'next/server';
import { AssistantConfig, DEFAULT_CONFIG } from '@/lib/openai';

// Server-side in-memory config store
let currentConfig: AssistantConfig = { ...DEFAULT_CONFIG };

export async function GET() {
  return NextResponse.json(currentConfig);
}

export async function POST(req: NextRequest) {
  try {
    const updates = await req.json();
    currentConfig = {
      ...currentConfig,
      ...updates,
      updatedAt: new Date().toISOString(),
    };

    return NextResponse.json({
      success: true,
      config: currentConfig,
    });
  } catch (error: any) {
    return NextResponse.json(
      { error: 'Sozlamalarni saqlashda xatolik yuz berdi' },
      { status: 500 }
    );
  }
}
