import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ClaimsOfficerService } from './claims-officer.service';
import { AuthService } from '../../core/auth.service';
import { AiService } from '../customer-dashboard/ai.service';
import { FormsModule } from '@angular/forms';

export interface Claim {
  claimId: number;
  customerName?: string;
  eventName: string;
  policyName?: string;
  claimAmount: number;
  incidentDate?: string;
  rejectionReason?: string;
  approvedAmount?: number;
  evidenceDocPath?: string;
  riskLevel?: string;
  filedAt: string;
  status: string;
}

interface Message {
  text: string;
  sender: 'user' | 'agent';
  actions?: string[];
}

@Component({
  selector: 'app-claims-officer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './claims-officer-dashboard.component.html',
})
export class ClaimsOfficerDashboardComponent implements OnInit {
  private readonly service = inject(ClaimsOfficerService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly aiService = inject(AiService);

  // Use the resource from ClaimsOfficerService
  readonly claimsResource = this.service.claimsResource;

  // Computed signal for easy access in template
  readonly allClaims = computed(() => {
    const res = this.claimsResource.value();
    return res?.data ?? res ?? [];
  });

  readonly filter = signal<'ALL' | 'ASSIGNED'>('ASSIGNED');
  readonly isLoading = this.claimsResource.isLoading;
  readonly actionError = signal<string | null>(null);
  
  readonly processingId = signal<number | null>(null);
  readonly rejectingId = signal<number | null>(null);
  readonly rejectionReason = signal('');

  readonly officerEmail = computed(() => this.authService.getEmail() || 'Officer');

  // Chatbot state
  readonly isChatOpen = signal(false);
  currentMessage = '';
  readonly chatHistory = signal<Message[]>([
    { text: 'Hello Claims Officer! I can help you verify incident details and analyze evidence documents. Which claim should we review?', sender: 'agent' }
  ]);
  readonly isTyping = signal(false);
  private pollingInterval: any;

  ngOnInit(): void {
    this.service.setFilter(this.filter());
    this.loadChatHistory();
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private startPolling(): void {
    if (typeof window !== 'undefined') {
      this.pollingInterval = setInterval(() => {
        this.service.reloadClaims();
      }, 10000);
    }
  }

  private stopPolling(): void {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
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
    if (act.includes('view assigned')) this.setFilter('ASSIGNED');
    else if (act.includes('view all')) this.setFilter('ALL');
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
  setFilter(f: 'ALL' | 'ASSIGNED'): void {
    this.filter.set(f);
    this.service.setFilter(f);
  }

  loadClaims(f: 'ALL' | 'ASSIGNED' = 'ASSIGNED'): void {
    // This is now handled by claimsResource
    this.service.setFilter(f);
  }

  viewDetails(id: number): void {
    this.router.navigate(['/claims-detail', id]);
  }

  toggleRejectForm(id: number): void {
    if (this.rejectingId() === id) {
      this.rejectingId.set(null);
      this.rejectionReason.set('');
    } else {
      this.rejectingId.set(id);
      this.rejectionReason.set('');
    }
    this.actionError.set(null);
  }

  onReasonInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.rejectionReason.set(target.value);
  }

  approve(id: number): void {
    this.processingId.set(id);
    this.actionError.set(null);
    this.service.approveClaim(id, {}).subscribe({
      next: () => {
        this.processingId.set(null);
        this.service.reloadClaims(); // Forced refresh
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to approve.');
        this.processingId.set(null);
      },
    });
  }

  reject(id: number): void {
    const reason = this.rejectionReason().trim();
    if (!reason) {
      this.actionError.set('Please provide a reason for rejection.');
      return;
    }

    this.processingId.set(id);
    this.actionError.set(null);
    this.service.rejectClaim(id, reason).subscribe({
      next: () => {
        this.processingId.set(null);
        this.rejectingId.set(null);
        this.service.reloadClaims(); // Forced refresh
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to reject.');
        this.processingId.set(null);
      },
    });
  }

  viewDocument(path: string | undefined): void {
    if (!path) {
      return;
    }
    const url = path.startsWith('http') ? path : `http://localhost:8080/uploads/${path}`;
    window.open(url, '_blank');
  }

  downloadReport(claimId: number): void {
    this.service.downloadClaimReport(claimId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Claim_Report_${claimId}.pdf`;
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
