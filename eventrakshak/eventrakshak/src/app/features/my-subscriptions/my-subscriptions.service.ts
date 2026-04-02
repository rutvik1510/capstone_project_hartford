import { inject, Injectable, signal } from '@angular/core'; // Add signal
import { HttpClient, httpResource } from '@angular/common/http'; // Add httpResource
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service'; // Add AuthService

@Injectable({ providedIn: 'root' })
export class MySubscriptionsService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly base = 'http://localhost:8080/subscriptions';

  private readonly refreshTrigger = signal(0);

  readonly subscriptionsResource = httpResource<any>(() => {
    this.refreshTrigger();
    if (!this.authService.isLoggedIn()) return undefined;
    return `${this.base}`;
  });

  reloadSubscriptions(): void {
    this.refreshTrigger.update((v: number) => v + 1);
  }

  getMySubscriptions(): Observable<unknown> {
    return this.http.get(this.base);
  }

  payPremium(subscriptionId: number): Observable<any> {
    return this.http.post(`${this.base}/${subscriptionId}/pay-premium`, {});
  }

  downloadPolicyReport(subscriptionId: number): Observable<Blob> {
    return this.http.get(`http://localhost:8080/api/reports/policy/${subscriptionId}`, {
      responseType: 'blob'
    });
  }
}
