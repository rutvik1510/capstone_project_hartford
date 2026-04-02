import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';

@Injectable({ providedIn: 'root' })
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly base = 'http://localhost:8080/events';

  private readonly refreshTrigger = signal(0);

  // Using httpResource for a more Signal-native way of fetching data
  // Adding authService.isLoggedIn() as a dependency ensures it refreshes when login state changes
  readonly myEventsResource = httpResource<any>(() => {
    this.refreshTrigger(); // Dependency on refresh trigger
    if (!this.authService.isLoggedIn()) return undefined;
    return `${this.base}`;
  });

  reloadMyEvents(): void {
    this.refreshTrigger.update((v: number) => v + 1);
  }

  createMusicConcert(eventData: any): Observable<unknown> {
    return this.http.post(`${this.base}/music`, eventData);
  }

  createCorporateConference(eventData: any): Observable<unknown> {
    return this.http.post(`${this.base}/corporate`, eventData);
  }

  getMyEvents(): Observable<any> {
    return this.http.get(`${this.base}`);
  }
}
