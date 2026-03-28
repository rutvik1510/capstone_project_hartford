import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatMessage {
  id?: number;
  message: string;
  sender: 'USER' | 'AI';
  timestamp: string;
}

export interface AiChatRequest {
  userQuery: string;
  eventId?: number | null;
  subscriptionId?: number | null;
}

export interface AiChatResponse {
  message: string;
  suggestedPolicyId: number | null;
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/ai';

  // Signal to store policy recommendation across components
  readonly suggestedPolicyId = signal<number | null>(null);

  sendMessage(request: AiChatRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/chat`, request);
  }

  getHistory(eventId?: number): Observable<any> {
    const url = eventId ? `${this.apiUrl}/history?eventId=${eventId}` : `${this.apiUrl}/history`;
    return this.http.get<any>(url);
  }

  analyzeSubscriptionDoc(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/analyze/subscription/${id}`);
  }

  analyzeClaimDoc(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/analyze/claim/${id}`);
  }
}
