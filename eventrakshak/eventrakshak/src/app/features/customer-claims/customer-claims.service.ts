import { inject, Injectable, signal } from '@angular/core'; // Add signal
import { HttpClient, httpResource } from '@angular/common/http'; // Add httpResource
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service'; // Add AuthService

@Injectable({ providedIn: 'root' })
export class CustomerClaimsService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly base = 'http://localhost:8080/claims';

  private readonly refreshTrigger = signal(0);

  readonly claimsResource = httpResource<any>(() => {
    this.refreshTrigger();
    if (!this.authService.isLoggedIn()) return undefined;
    return `${this.base}`;
  });

  reloadClaims(): void {
    this.refreshTrigger.update((v: number) => v + 1);
  }

  getClaims(): Observable<any> {
    return this.http.get(this.base);
  }

  collectClaim(claimId: number): Observable<any> {
    return this.http.put(`${this.base}/${claimId}/collect`, {});
  }

  downloadClaimReport(claimId: number): Observable<Blob> {
    return this.http.get(`http://localhost:8080/api/reports/claim/${claimId}`, {
      responseType: 'blob'
    });
  }
}
