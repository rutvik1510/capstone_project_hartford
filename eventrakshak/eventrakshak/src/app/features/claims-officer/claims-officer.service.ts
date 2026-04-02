import { inject, Injectable, signal } from '@angular/core'; // Add signal
import { HttpClient, httpResource } from '@angular/common/http'; // Add httpResource
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service'; // Add AuthService

@Injectable({ providedIn: 'root' })
export class ClaimsOfficerService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly base = 'http://localhost:8080/claims-officer/claims';

  private readonly refreshTrigger = signal(0);
  private readonly filter = signal<'ALL' | 'ASSIGNED'>('ASSIGNED');

  readonly claimsResource = httpResource<any>(() => {
    this.refreshTrigger();
    if (!this.authService.isLoggedIn()) return undefined;
    const f = this.filter();
    return f === 'ALL' ? this.base : `${this.base}/assigned`;
  });

  reloadClaims(): void {
    this.refreshTrigger.update((v: number) => v + 1);
  }

  setFilter(f: 'ALL' | 'ASSIGNED'): void {
    this.filter.set(f);
  }

  getClaims(): Observable<any> {
    return this.http.get(this.base);
  }

  getAllClaims(): Observable<any> {
    return this.getClaims();
  }

  getAssignedClaims(): Observable<any> {
    return this.http.get(`${this.base}/assigned`);
  }

  getClaimDetails(id: number): Observable<any> {
    return this.http.get(`${this.base}/${id}`);
  }

  approveClaim(id: number, data: any): Observable<any> {
    return this.http.put(`${this.base}/${id}/approve`, data);
  }

  rejectClaim(id: number, reason: string, internalRemarks?: string): Observable<any> {
    return this.http.put(`${this.base}/${id}/reject`, { reason, internalRemarks });
  }

  updateClaimRemarks(id: number, internalRemarks: string, verificationChecklist: string): Observable<any> {
    return this.http.put(`${this.base}/${id}/update-remarks`, { internalRemarks, verificationChecklist });
  }

  downloadClaimReport(claimId: number): Observable<Blob> {
    return this.http.get(`http://localhost:8080/api/reports/claim/${claimId}`, {
      responseType: 'blob'
    });
  }
}
