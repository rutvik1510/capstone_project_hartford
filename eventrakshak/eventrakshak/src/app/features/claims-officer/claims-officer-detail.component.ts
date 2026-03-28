import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ClaimsOfficerService } from './claims-officer.service';
import { AiService } from '../customer-dashboard/ai.service';

export interface ClaimDetail {
  claimId: number;
  subscriptionId?: number;
  customerName?: string;
  customerPhone?: string;
  eventName?: string;
  eventType?: string;
  eventDate?: string;
  location?: string;
  numberOfAttendees?: number;
  budget?: number;
  policyName?: string;
  baseRate?: number;
  maxCoverageAmount?: number;
  premiumAmount?: number;
  claimAmount: number;
  incidentDate?: string;
  approvedAmount?: number;
  evidenceDocPath?: string;
  description?: string;
  filedAt: string;
  status: string;
  rejectionReason?: string;
  assignedOfficerName?: string;
  eventRisk?: number;
  weatherRisk?: number;
  totalRisk?: number;
  riskLevel?: string;
  temperature?: number;
  humidity?: number;
  windSpeed?: number;
  weatherCondition?: string;
  internalRemarks?: string;
  verificationChecklist?: string;
}

@Component({
  selector: 'app-claims-officer-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './claims-officer-detail.component.html',
})
export class ClaimsOfficerDetailComponent implements OnInit {
  private readonly service = inject(ClaimsOfficerService);
  private readonly aiService = inject(AiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly claimId = Number(this.route.snapshot.paramMap.get('id'));
  readonly claim = signal<ClaimDetail | null>(null);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly processingAction = signal<string | null>(null);

  readonly showRejectForm = signal(false);
  readonly rejectionReason = signal('');

  // Internal Audit Features
  readonly internalRemarks = signal('');
  readonly checklist = signal({
    evidenceMatches: false,
    noNegligence: false,
    withinCoverageLimit: false
  });

  // Smart Summary
  readonly docSummary = signal<string | null>(null);
  readonly isAnalyzing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.service.getClaimDetails(this.claimId).subscribe({
      next: (res: any) => {
        const data = res.data || res;
        this.claim.set(data);
        this.internalRemarks.set(data.internalRemarks || '');
        if (data.verificationChecklist) {
          try {
            this.checklist.set(JSON.parse(data.verificationChecklist));
          } catch (e) {
            console.error('Failed to parse checklist JSON', e);
          }
        }
        this.isLoading.set(false);
      },
      error: (err: any) => {
        this.errorMessage.set(err?.error?.message ?? 'Failed to load claim details.');
        this.isLoading.set(false);
      },
    });
  }

  fetchDocSummary(): void {
    this.isAnalyzing.set(true);
    this.docSummary.set(null);
    this.aiService.analyzeClaimDoc(this.claimId).subscribe({
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

  statusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PENDING': return 'bg-amber-100 text-amber-700 border border-amber-200';
      case 'APPROVED': return 'bg-green-100 text-green-700 border border-green-200';
      case 'REJECTED': return 'bg-red-100 text-red-700 border border-red-200';
      default: return 'bg-slate-100 text-slate-700 border border-slate-200';
    }
  }

  riskLevelClass(level: string | undefined): string {
    switch (level?.toUpperCase()) {
      case 'LOW': return 'bg-green-100 text-green-700';
      case 'MEDIUM': return 'bg-amber-100 text-amber-700';
      case 'HIGH': return 'bg-red-100 text-red-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  toggleRejectForm(): void {
    this.showRejectForm.update(v => !v);
  }

  onReasonInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.rejectionReason.set(target.value);
  }

  updateInternalRemarks(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.internalRemarks.set(target.value);
  }

  toggleChecklist(key: keyof ReturnType<typeof this.checklist>): void {
    this.checklist.update(c => ({ ...c, [key]: !c[key] }));
  }

  isChecklistComplete(): boolean {
    const c = this.checklist();
    return c.evidenceMatches && c.noNegligence && c.withinCoverageLimit;
  }

  saveRemarks(): void {
    this.processingAction.set('save');
    this.service.updateClaimRemarks(
      this.claimId, 
      this.internalRemarks(), 
      JSON.stringify(this.checklist())
    ).subscribe({
      next: () => {
        this.processingAction.set(null);
        this.successMessage.set('Remarks and checklist saved.');
        setTimeout(() => this.successMessage.set(null), 3000);
      },
      error: () => {
        this.actionError.set('Failed to save remarks.');
        this.processingAction.set(null);
      }
    });
  }

  approve(): void {
    if (!this.isChecklistComplete()) {
      this.actionError.set('Please complete the verification checklist before approving.');
      return;
    }
    
    this.processingAction.set('approve');
    this.actionError.set(null);
    this.successMessage.set(null);

    // Save remarks first then approve
    const remarks = this.internalRemarks();
    this.service.updateClaimRemarks(
      this.claimId, 
      remarks, 
      JSON.stringify(this.checklist())
    ).subscribe({
      next: () => {
        this.service.approveClaim(this.claimId, { internalRemarks: remarks }).subscribe({
          next: () => {
            this.processingAction.set(null);
            this.successMessage.set('Claim approved successfully.');
            this.load();
          },
          error: (err: any) => {
            this.actionError.set(err?.error?.message ?? 'Failed to approve claim.');
            this.processingAction.set(null);
          },
        });
      }
    });
  }

  reject(): void {
    const reason = this.rejectionReason().trim();
    const remarks = this.internalRemarks().trim();
    
    if (!reason) {
      this.actionError.set('Please provide a reason for rejection.');
      return;
    }

    this.processingAction.set('reject');
    this.actionError.set(null);
    this.successMessage.set(null);

    // Save current checklist/remarks state first
    this.service.updateClaimRemarks(
      this.claimId, 
      remarks, 
      JSON.stringify(this.checklist())
    ).subscribe({
      next: () => {
        this.service.rejectClaim(this.claimId, reason, remarks).subscribe({
          next: () => {
            this.processingAction.set(null);
            this.showRejectForm.set(false);
            this.successMessage.set('Claim rejected successfully.');
            this.load();
          },
          error: (err: any) => {
            this.actionError.set(err?.error?.message ?? 'Failed to reject claim.');
            this.processingAction.set(null);
          },
        });
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/claims-dashboard']);
  }

  viewDocument(path: string | undefined): void {
    if (!path) {
      alert('No evidence document provided.');
      return;
    }
    const url = path.startsWith('http') ? path : `http://localhost:8080/uploads/${path}`;
    window.open(url, '_blank');
  }
}
