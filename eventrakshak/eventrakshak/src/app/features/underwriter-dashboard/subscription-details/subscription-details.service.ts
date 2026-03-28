import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SubscriptionDetailsService {
  private readonly http = inject(HttpClient);

  getSubscriptionDetails(id: number): Observable<any> {
    return this.http.get(`http://localhost:8080/underwriter/subscriptions/${id}`);
  }

  approveSubscription(id: number, payload: any): Observable<any> {
    return this.http.put(`http://localhost:8080/underwriter/subscriptions/${id}/approve`, payload);
  }

  rejectSubscription(id: number, reason?: string, notes?: string): Observable<any> {
    const payload: any = {};
    if (reason) payload.reason = reason;
    if (notes) payload.underwriterNotes = notes;
    
    return this.http.put(`http://localhost:8080/underwriter/subscriptions/${id}/reject`, payload);
  }
}
