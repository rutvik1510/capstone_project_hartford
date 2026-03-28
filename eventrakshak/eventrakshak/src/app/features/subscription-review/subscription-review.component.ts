import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SubscriptionReviewService } from './subscription-review.service';
import { AiService } from '../customer-dashboard/ai.service';

export interface SubscriptionDetail {
  subscriptionId: number;
  eventName: string;
  eventType?: string;
  customerName?: string;
  customerPhone?: string;
  policyName?: string;
  policyDescription?: string;
  baseRate?: number;
  maxCoverageAmount?: number;
  premiumAmount?: number;
  riskPercentage?: number;
  riskLevel?: string;
  riskFactors?: string;
  status: string;
  rejectionReason?: string;
  underwriterNotes?: string;
  assignedUnderwriterName?: string;
  location?: string;
  eventDate?: string;
  numberOfAttendees?: number;
  budget?: number;
  venueType?: string;
  durationInDays?: number;
  isOutdoor?: boolean;
  alcoholAllowed?: boolean;
  fireworksUsed?: boolean;
  celebrityInvolved?: boolean;
  temporaryStructure?: boolean;
  locationRiskLevel?: string;
  securityLevel?: string;
  temperature?: number;
  windSpeed?: number;
  humidity?: number;
  weatherCondition?: string;
  eventRisk?: number;
  weatherRisk?: number;
  totalRisk?: number;
  hasProfessionalSecurity?: boolean;
  hasCCTV?: boolean;
  hasMetalDetectors?: boolean;
  hasFireNOC?: boolean;
  hasOnSiteFireSafety?: boolean;
  safetyComplianceDocPath?: string;
  premiumOverrideAmount?: number;
  overrideReason?: string;
}

@Component({
  selector: 'app-subscription-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './subscription-review.component.html',
})
export class SubscriptionReviewComponent implements OnInit {
  private readonly service = inject(SubscriptionReviewService);
  private readonly aiService = inject(AiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly sub = signal<SubscriptionDetail | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly processingAction = signal<string | null>(null);
  
  readonly showRejectForm = signal(false);
  readonly rejectionReason = signal('');
  readonly underwriterNotes = signal('');

  readonly showAdjustmentForm = signal(false);
  readonly adjustedPremium = signal<number | null>(null);
  readonly adjustmentReason = signal('');

  // Smart Summary
  readonly docSummary = signal<string | null>(null);
  readonly isAnalyzing = signal(false);

  private subscriptionId!: number;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.subscriptionId = Number(idParam);
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.service.getDetails(this.subscriptionId).subscribe({
      next: (res: any) => {
        const data = res.data ?? res;
        this.sub.set(data);
        this.adjustedPremium.set(data.premiumAmount);
        this.underwriterNotes.set(data.underwriterNotes || '');
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to load subscription details.');
        this.isLoading.set(false);
      },
    });
  }

  fetchDocSummary(): void {
    this.isAnalyzing.set(true);
    this.docSummary.set(null);
    this.aiService.analyzeSubscriptionDoc(this.subscriptionId).subscribe({
      next: (res: any) => {
        this.docSummary.set(res.data || res);
        this.isAnalyzing.set(false);
      },
      error: () => {
        this.docSummary.set('Failed to generate summary.');
        this.isAnalyzing.set(false);
      }
    });
  }

  statusBadgeClass(status: string | undefined): string {
    switch (status?.toUpperCase()) {
      case 'APPROVED': return 'bg-green-100 text-green-700';
      case 'REJECTED': return 'bg-red-100 text-red-700';
      case 'PAID':     return 'bg-blue-100 text-blue-700';
      default:         return 'bg-yellow-100 text-yellow-700';
    }
  }

  toggleRejectForm(): void {
    this.showRejectForm.update(v => !v);
    this.showAdjustmentForm.set(false);
    this.rejectionReason.set('');
    this.actionError.set(null);
  }

  toggleAdjustmentForm(): void {
    this.showAdjustmentForm.update(v => !v);
    this.showRejectForm.set(false);
    this.actionError.set(null);
  }

  onReasonInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.rejectionReason.set(target.value);
  }

  onPremiumInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.adjustedPremium.set(Number(target.value));
  }

  onAdjustmentReasonInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.adjustmentReason.set(target.value);
  }

  approve(): void {
    this.processingAction.set('approve');
    this.actionError.set(null);
    this.successMessage.set(null);

    const payload: any = {
      underwriterNotes: this.underwriterNotes()
    };

    if (this.showAdjustmentForm()) {
      if (!this.adjustedPremium() || this.adjustedPremium()! <= 0) {
        this.actionError.set('Please enter a valid premium amount.');
        this.processingAction.set(null);
        return;
      }
      if (!this.adjustmentReason().trim()) {
        this.actionError.set('Please provide a reason for the price adjustment.');
        this.processingAction.set(null);
        return;
      }
      payload.premiumOverrideAmount = this.adjustedPremium();
      payload.overrideReason = this.adjustmentReason();
    }

    this.service.approve(this.subscriptionId, payload).subscribe({
      next: () => {
        this.processingAction.set(null);
        this.showAdjustmentForm.set(false);
        this.successMessage.set('Subscription approved successfully.');
        this.load();
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to approve subscription.');
        this.processingAction.set(null);
      },
    });
  }

  reject(): void {
    const reason = this.rejectionReason().trim();
    if (!reason) {
      this.actionError.set('Please provide a reason for rejection.');
      return;
    }

    this.processingAction.set('reject');
    this.actionError.set(null);
    this.successMessage.set(null);
    this.service.reject(this.subscriptionId, reason, this.underwriterNotes()).subscribe({
      next: () => {
        this.processingAction.set(null);
        this.showRejectForm.set(false);
        this.successMessage.set('Subscription rejected.');
        this.load();
      },
      error: (err: any) => {
        this.actionError.set(err?.error?.message ?? 'Failed to reject subscription.');
        this.processingAction.set(null);
      },
    });
  }

  goBack(): void {
    this.router.navigate(['/underwriter-dashboard']);
  }

  viewDocument(path: string | undefined): void {
    if (!path) return;
    const url = path.startsWith('http') ? path : `http://localhost:8080/uploads/${path}`;
    window.open(url, '_blank');
  }
}
