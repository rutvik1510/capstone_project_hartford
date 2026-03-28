import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ClaimsOfficerService {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/claims-officer/claims';

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
