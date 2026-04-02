import { Component, inject, signal, OnInit, computed, effect } from '@angular/core'; // Add effect
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CustomerClaimsService } from './customer-claims.service';
import { AuthService } from '../../core/auth.service';

export interface CustomerClaim {
  claimId: number;
  eventName: string;
  claimAmount: number;
  incidentDate?: string;
  rejectionReason?: string;
  internalRemarks?: string;
  description?: string;
  evidenceDocPath?: string;
  approvedAmount?: number;
  filedAt: string;
  status: string;
  assignedOfficerName?: string;
}

@Component({
  selector: 'app-customer-claims',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './customer-claims.component.html',
})
export class CustomerClaimsComponent implements OnInit {
  private readonly service = inject(CustomerClaimsService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // Use the resource from CustomerClaimsService
  readonly claimsResource = this.service.claimsResource;

  // Computed signal for easy access in template
  readonly claims = computed(() => {
    const res = this.claimsResource.value();
    return res?.data ?? res ?? [];
  });

  readonly isLoading = this.claimsResource.isLoading;
  readonly errorMessage = signal<string | null>(null);

  readonly successMessage = signal<string | null>(null);
  readonly isCollecting = signal<number | null>(null);
  readonly isConfirming = signal<number | null>(null);

  constructor() {
    // Sync errorMessage with resource error
    effect(() => {
      if (this.claimsResource.error()) {
        this.errorMessage.set('Failed to load claims.');
      }
    });
  }

  ngOnInit(): void {
    // We can still trigger a reload manually if needed,
    // but httpResource handles initial fetch automatically
    this.service.reloadClaims();
  }

  showConfirm(id: number): void {
    this.isConfirming.set(id);
  }

  cancelConfirm(): void {
    this.isConfirming.set(null);
  }

  private loadClaims(): void {
    // This is now handled by claimsResource
    this.service.reloadClaims();
  }

  isPaid(status: string): boolean {
    const s = status?.toUpperCase();
    return s === 'COLLECTED' || s === 'PAID' || s === 'SETTLED' || s === 'CLOSED';
  }

  receivePayment(claimId: number): void {
    this.isCollecting.set(claimId);
    this.isConfirming.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    
    this.service.collectClaim(claimId).subscribe({
      next: () => {
        this.successMessage.set('Payout successfully transferred!');
        this.isCollecting.set(null);
        this.loadClaims();
        setTimeout(() => this.successMessage.set(null), 5000);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message ?? 'Collection failed.');
        this.isCollecting.set(null);
      }
    });
  }

  statusClass(status: string): string {
    const s = status?.toUpperCase();
    switch (s) {
      case 'APPROVED': return 'bg-blue-100 text-blue-700'; 
      case 'REJECTED': return 'bg-red-100 text-red-700';
      case 'COLLECTED':
      case 'PAID':
      case 'SETTLED':  return 'bg-green-100 text-green-700';
      default:         return 'bg-yellow-100 text-yellow-700';
    }
  }

  stageProgress(status: string): number {
    const s = status?.toUpperCase();
    if (s === 'COLLECTED' || s === 'PAID' || s === 'SETTLED') return 3;
    if (s === 'APPROVED' || s === 'REJECTED') return 2;
    return 1;
  }

  viewDocument(path: string | undefined): void {
    if (!path) {
      alert('No evidence document provided.');
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
      error: () => this.errorMessage.set('Failed to download claim report.')
    });
  }

  viewReport(claimId: number): void {
    this.service.downloadClaimReport(claimId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
      },
      error: () => this.errorMessage.set('Failed to view report.')
    });
  }

  goBack(): void {
    this.router.navigate(['/customer-dashboard']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
