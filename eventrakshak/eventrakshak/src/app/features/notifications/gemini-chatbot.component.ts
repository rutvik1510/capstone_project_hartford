import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AiService } from '../customer-dashboard/ai.service';

interface Message {
  text: string;
  sender: 'user' | 'agent';
  actions?: string[];
}

@Component({
  standalone: true,
  selector: 'app-gemini-chatbot',
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Floating Chatbot UI -->
    <div class="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 z-50 flex flex-col items-end">
      <!-- Chat Window -->
      <div *ngIf="isChatOpen()"
        class="mb-4 w-[calc(100vw-2rem)] sm:w-96 bg-white rounded-3xl shadow-2xl flex flex-col overflow-hidden border border-slate-100"
        style="height: 500px; max-height: calc(100vh - 10rem); box-shadow: 0 20px 50px rgba(0,0,0,0.15)">
        
        <!-- Header -->
        <div class="px-6 py-4 flex items-center justify-between"
          style="background: linear-gradient(135deg, #8C1D40, #6E1733)">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center">
              <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            <span class="text-white font-black tracking-tight">Event Buddy</span>
          </div>
          <button (click)="toggleChat()" class="text-white/60 hover:text-white transition-colors">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Messages -->
        <div class="flex-1 overflow-y-auto p-6 space-y-4 bg-slate-50">
          <div *ngFor="let msg of chatHistory()" class="flex flex-col" [ngClass]="{'items-end': msg.sender === 'user'}">
            <div class="max-w-[85%] px-4 py-2.5 rounded-2xl text-sm font-medium leading-relaxed"
              [ngClass]="msg.sender === 'user' 
                ? 'bg-[#1F2937] text-white rounded-br-none shadow-md' 
                : 'bg-white text-slate-700 border border-slate-200 rounded-bl-none shadow-sm'">
              {{ msg.text }}
            </div>
            
            <!-- Action Buttons -->
            <div *ngIf="msg.actions && msg.actions.length > 0" class="flex flex-wrap gap-2 mt-2">
              <button *ngFor="let action of msg.actions" (click)="handleAction(action)"
                class="px-3 py-1.5 bg-white border border-[#8C1D40]/30 text-[#8C1D40] text-[10px] font-black uppercase tracking-widest rounded-full hover:bg-[#8C1D40] hover:text-white transition-all shadow-sm">
                {{ action }}
              </button>
            </div>
          </div>
          <div *ngIf="isTyping()" class="flex">
            <div class="bg-white border border-slate-200 px-4 py-2.5 rounded-2xl rounded-bl-none shadow-sm flex gap-1 items-center">
              <div class="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce"></div>
              <div class="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 0.1s"></div>
              <div class="w-1.5 h-1.5 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 0.2s"></div>
            </div>
          </div>
        </div>

        <!-- Input -->
        <div class="p-4 bg-white border-t border-slate-100 flex gap-2">
          <input type="text" [(ngModel)]="currentMessage" (keyup.enter)="sendMessage()"
            placeholder="Ask about coverage, claims..."
            class="flex-1 bg-slate-50 border-none rounded-xl px-4 py-2 text-sm font-medium focus:ring-2 focus:ring-[#8C1D40]/20 transition-all outline-none">
          <button (click)="sendMessage()"
            class="w-10 h-10 rounded-xl flex items-center justify-center text-white transition-all bg-[#1F2937] hover:bg-[#8C1D40] disabled:opacity-50"
            [disabled]="!currentMessage.trim()">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="3" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      <!-- Toggle Button -->
      <button (click)="toggleChat()"
        class="w-16 h-16 rounded-full shadow-2xl flex items-center justify-center text-white transition-all transform hover:scale-110 active:scale-95"
        [ngClass]="isChatOpen() ? 'bg-slate-800' : 'bg-[#8C1D40]'"
        style="box-shadow: 0 10px 30px rgba(140,29,64,0.4)">
        <svg *ngIf="!isChatOpen()" class="w-7 h-7" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
        </svg>
        <svg *ngIf="isChatOpen()" class="w-7 h-7" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
    </div>
  `
})
export class GeminiChatbotComponent implements OnInit {
  private readonly aiService = inject(AiService);
  private readonly router = inject(Router);

  @Input() eventId: number | null = null;

  readonly isChatOpen = signal(false);
  currentMessage = '';
  readonly chatHistory = signal<Message[]>([
    { text: 'Hello! I am your EventGuard assistant. How can I help you today?', sender: 'agent' }
  ]);
  readonly isTyping = signal(false);

  ngOnInit(): void {
    this.loadChatHistory();
  }

  loadChatHistory(): void {
    this.aiService.getHistory(this.eventId || undefined).subscribe({
      next: (res: any) => {
        const dbMessages = res.data ?? [];
        if (dbMessages.length > 0) {
          const mappedMessages: Message[] = dbMessages.map((m: any) => ({
            text: m.message,
            sender: m.sender === 'USER' ? 'user' : 'agent'
          }));
          this.chatHistory.set(mappedMessages);
        }
      },
      error: () => console.error('Failed to load chat history')
    });
  }

  toggleChat(): void {
    this.isChatOpen.update(v => !v);
  }

  handleAction(action: string): void {
    const act = action.toLowerCase();
    if (act.includes('view my subscriptions') || act.includes('my subscriptions')) {
      this.router.navigate(['/my-subscriptions']);
    } else if (act.includes('pay premium')) {
      this.router.navigate(['/my-subscriptions']);
    } else if (act.includes('file claim')) {
      this.router.navigate(['/file-claim']);
    } else if (act.includes('view my events') || act.includes('my events')) {
      this.router.navigate(['/my-events']);
    } else if (act.includes('create event')) {
      this.router.navigate(['/create-event']);
    } else if (act.includes('track claim') || act.includes('my claims')) {
      this.router.navigate(['/my-claims']);
    } else {
      this.currentMessage = action;
      this.sendMessage();
    }
  }

  sendMessage(): void {
    const msg = this.currentMessage.trim();
    if (!msg) return;

    this.chatHistory.update(history => [...history, { text: msg, sender: 'user' }]);
    this.currentMessage = '';
    this.isTyping.set(true);

    this.aiService.sendMessage({ 
      userQuery: msg, 
      eventId: this.eventId 
    }).subscribe({
      next: (res: any) => {
        const data = res.data;
        this.chatHistory.update(history => [...history, { 
          text: data.message, 
          sender: 'agent',
          actions: data.actions 
        }]);
        if (data.suggestedPolicyId) {
          this.aiService.suggestedPolicyId.set(data.suggestedPolicyId);
        }
        this.isTyping.set(false);
      },
      error: () => {
        this.chatHistory.update(history => [...history, { text: 'Sorry, I encountered an error. Please try again later.', sender: 'agent' }]);
        this.isTyping.set(false);
      }
    });
  }
}
