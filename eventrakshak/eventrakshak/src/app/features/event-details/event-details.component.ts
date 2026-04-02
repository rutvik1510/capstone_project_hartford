import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { EventDetailsService } from './event-details.service';
import { AiService } from '../customer-dashboard/ai.service';
import { GeminiChatbotComponent } from '../notifications/gemini-chatbot.component';

@Component({
  selector: 'app-event-details',
  standalone: true,
  imports: [CommonModule, RouterModule, GeminiChatbotComponent],
  templateUrl: './event-details.component.html',
})
export class EventDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly detailsService = inject(EventDetailsService);
  protected readonly aiService = inject(AiService);

  readonly eventId = signal(Number(this.route.snapshot.paramMap.get('id')));
  readonly event = signal<any>(null);
  readonly policies = signal<any[]>([]);
  readonly quotes = signal<Record<number, any>>({});
  readonly subscribedPolicyIds = signal<Set<number>>(new Set());
  readonly hasPaidPolicy = signal(false);
  readonly hasAnySubscription = signal(false);
  readonly isEventLocked = signal(false);
  
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly subscribingPolicyId = signal<number | null>(null);
  readonly quoteResult = signal<any>(null);
  readonly subscribeError = signal<string | null>(null);

  readonly availablePolicies = computed(() => {
    return this.policies();
  });

  ngOnInit(): void {
    this.loadAllData();
  }

  private loadAllData(): void {
    this.isLoading.set(true);
    const id = this.eventId();

    this.detailsService.getEventById(id).subscribe({
      next: (res: any) => {
        const eventData = res.data ?? res;
        this.event.set(eventData);
        this.isEventLocked.set(!!eventData.isLocked);
        const domain = eventData.eventType ?? eventData.domain ?? '';

        // Load Subscriptions
        this.detailsService.getMySubscriptions().subscribe((subRes: any) => {
          const subs = subRes.data ?? subRes ?? [];
          const eventSubs = subs.filter((s: any) => (s.event?.eventId ?? s.eventId) === id);
          
          const ids = new Set<number>(eventSubs.map((s: any) => s.policy?.policyId ?? s.policyId));
          this.subscribedPolicyIds.set(ids);

          // Check for any active (not rejected) subscription
          const activeSub = eventSubs.find((s: any) => s.status?.toUpperCase() !== 'REJECTED');
          this.hasAnySubscription.set(!!activeSub);

          // Check for paid policy
          const paidPolicy = eventSubs.find((s: any) => 
            s.status?.toUpperCase() === 'PAID' || 
            s.isPaid
          );
          this.hasPaidPolicy.set(!!paidPolicy);

          // If there's already a subscription, show the result section automatically
          if (eventSubs.length > 0) {
            this.quoteResult.set(eventSubs[0]);
          }
        });

        // Load Quotes (which includes policy details + calculated premium)
        this.detailsService.getQuotesForEvent(id).subscribe({
          next: (qRes: any) => {
            const quoteData = qRes.data ?? qRes ?? [];
            const quotesMap: Record<number, any> = {};
            
            // Extract unique policies from quotes for the cards
            const pols: any[] = [];
            quoteData.forEach((q: any) => {
              quotesMap[q.policyId] = q;
              pols.push({
                policyId: q.policyId,
                id: q.policyId,
                policyName: q.policyName,
                description: q.policyDescription || '', // Base rates and other info are also in quotes
                baseRate: q.baseRate,
                maxCoverageAmount: q.maxCoverageAmount,
                // We'll need to make sure the backend quote response includes coverage flags
                // For now, let's assume we still need the base policy for coverage flags or add them to DTO
              });
            });
            
            this.quotes.set(quotesMap);

            // Fetch full policies for coverage flags (coversTheft, etc.)
            this.detailsService.getPoliciesByDomain(domain).subscribe({
              next: (pRes: any) => {
                this.policies.set(pRes.data ?? pRes ?? []);
                this.isLoading.set(false);
              },
              error: () => { this.errorMessage.set('Failed to load policies.'); this.isLoading.set(false); }
            });
          },
          error: () => { this.errorMessage.set('Failed to calculate premiums.'); this.isLoading.set(false); }
        });
      },
      error: () => { this.errorMessage.set('Failed to load event.'); this.isLoading.set(false); }
    });
  }

  subscribe(policyId: number): void {
    this.subscribingPolicyId.set(policyId);
    this.quoteResult.set(null);
    this.subscribeError.set(null);

    this.detailsService.createSubscription(this.eventId(), policyId).subscribe({
      next: (res: any) => {
        this.quoteResult.set(res.data ?? res);
        this.subscribingPolicyId.set(null);
        this.loadAllData();
      },
      error: (err) => {
        this.subscribeError.set(err?.error?.message ?? 'Subscription failed.');
        this.subscribingPolicyId.set(null);
      },
    });
  }

  goBack(): void {
    this.router.navigate(['/my-events']);
  }
}
