import { inject, Injectable } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';

@Injectable({ providedIn: 'root' })
export class EventService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly base = 'http://localhost:8080/events';

  // Using httpResource for a more Signal-native way of fetching data
  // Adding authService.isLoggedIn() as a dependency ensures it refreshes when login state changes
  readonly myEventsResource = httpResource<any>(() => {
    if (!this.authService.isLoggedIn()) return undefined;
    return `${this.base}`;
  });

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
