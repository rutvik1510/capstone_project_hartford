import { Component, inject, signal, computed, effect, afterNextRender, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { UnderwriterDashboardService } from './underwriter-dashboard.service';
import { AuthService } from '../../core/auth.service';
import { NotificationDropdownComponent } from '../notifications/notification-dropdown.component';
import { AiService } from '../customer-dashboard/ai.service';
import { FormsModule } from '@angular/forms';

export interface UnderwriterSubscription {
  subscriptionId: number;
  eventName: string;
  customerName: string;
  policyName: string;
  riskPercentage: number;
  premiumAmount: number;
  status: string;
  assignedUnderwriterName?: string;
  safetyComplianceDocPath?: string;
}

interface Message {
  text: string;
  sender: 'user' | 'agent';
  actions?: string[];
}

@Component({
  selector: 'app-underwriter-dashboard',
  standalone: true,
  imports: [CommonModule, NotificationDropdownComponent, FormsModule],
  templateUrl: './underwriter-dashboard.component.html',
})
export class UnderwriterDashboardComponent implements OnInit {
  private readonly service = inject(UnderwriterDashboardService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly aiService = inject(AiService);

  readonly currentUsername = computed(() => this.authService.userName());

  readonly subscriptions = signal<UnderwriterSubscription[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly processingId = signal<number | null>(null);

  // Chatbot state
  readonly isChatOpen = signal(false);
  currentMessage = '';
  readonly chatHistory = signal<Message[]>([
    { text: 'Hello Underwriter! I can help you summarize risks and audit safety documents. Which subscription should we look at?', sender: 'agent' }
  ]);
  readonly isTyping = signal(false);

  ngOnInit(): void {
    this.loadChatHistory();
  }

  loadChatHistory(): void {
    this.aiService.getHistory().subscribe({
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
    if (act.includes('view assigned')) this.loadSubscriptions();
    else {
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

    this.aiService.sendMessage({ userQuery: msg }).subscribe({
      next: (res: any) => {
        const data = res.data;
        this.chatHistory.update(history => [...history, { 
          text: data.message, 
          sender: 'agent',
          actions: data.actions 
        }]);
        this.isTyping.set(false);
      },
      error: () => {
        this.chatHistory.update(history => [...history, { text: 'AI currently unavailable.', sender: 'agent' }]);
        this.isTyping.set(false);
      }
    });
  }
  constructor() {
    afterNextRender(() => {
      this.loadSubscriptions();
    });
  }

  loadSubscriptions(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    
    this.service.getAssignedSubscriptions().subscribe({
      next: (res: any) => {
        this.subscriptions.set(res.data ?? res ?? []);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load subscriptions.');
        this.isLoading.set(false);
      },
    });
  }

  approve(id: number): void {
    this.processingId.set(id);
    this.actionError.set(null);
    this.service.approveSubscription(id).subscribe({
      next: () => {
        this.processingId.set(null);
        this.loadSubscriptions();
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to approve.');
        this.processingId.set(null);
      },
    });
  }

  reject(id: number): void {
    this.processingId.set(id);
    this.actionError.set(null);
    this.service.rejectSubscription(id).subscribe({
      next: () => {
        this.processingId.set(null);
        this.loadSubscriptions();
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to reject.');
        this.processingId.set(null);
      },
    });
  }

  statusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'APPROVED': return 'bg-green-100 text-green-700';
      case 'REJECTED': return 'bg-red-100 text-red-700';
      default:         return 'bg-yellow-100 text-yellow-700';
    }
  }

  viewDetails(id: number): void {
    this.router.navigate(['/underwriter/subscription', id]);
  }

  viewDocument(path: string | undefined): void {
    if (!path) {
      alert('No document uploaded.');
      return;
    }
    const url = path.startsWith('http') ? path : `http://localhost:8080/uploads/${path}`;
    window.open(url, '_blank');
  }

  downloadReport(subscriptionId: number): void {
    this.service.downloadPolicyReport(subscriptionId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Policy_Report_${subscriptionId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.actionError.set('Failed to download report.')
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
