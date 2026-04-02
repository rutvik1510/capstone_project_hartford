import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { AiService } from '../customer-dashboard/ai.service';

@Component({
  selector: 'app-policy-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="min-h-screen bg-[#F9FAFB] py-10 px-4">
      <div class="max-w-6xl mx-auto">

        <!-- Header -->
        <div class="flex items-center justify-between mb-10">
          <div>
            <h1 class="text-3xl font-black text-[#1F2937] tracking-tight">Insurance Policies</h1>
            <p class="text-slate-500 font-medium mt-1">Explore our coverage tiers and protection plans.</p>
          </div>
          <button (click)="goBack()" class="px-5 py-2.5 rounded-xl bg-white border border-slate-200 text-sm font-bold text-slate-700 hover:bg-slate-50 transition-all shadow-sm flex items-center gap-2">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M15 19l-7-7 7-7" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
            Back to Dashboard
          </button>
        </div>

        <!-- Filter Dropdown -->
        <div class="mb-10 flex flex-col sm:flex-row sm:items-center gap-4 bg-white p-6 rounded-3xl border border-slate-100 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-[#8C1D40]/10 flex items-center justify-center">
              <svg class="w-5 h-5 text-[#8C1D40]" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
                <path d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
              </svg>
            </div>
            <span class="text-sm font-black text-[#1F2937] uppercase tracking-widest">Filter by Event Type</span>
          </div>
          <div class="relative flex-1 max-w-xs">
            <select
              (change)="onDomainChange($event)"
              class="w-full appearance-none bg-slate-50 border border-slate-200 text-[#1F2937] text-sm font-bold rounded-2xl px-5 py-3 pr-10 outline-none focus:ring-2 focus:ring-[#8C1D40]/20 transition-all cursor-pointer">
              <option value="ALL">All Categories</option>
              <option value="CORPORATE_TECH_CONFERENCE">Corporate Events</option>
              <option value="OUTDOOR_MUSIC_CONCERT">Music Concerts</option>
            </select>
            <div class="absolute inset-y-0 right-0 flex items-center pr-4 pointer-events-none">
              <svg class="w-4 h-4 text-[#8C1D40]" fill="none" stroke="currentColor" stroke-width="3" viewBox="0 0 24 24">
                <path d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
          <div class="ml-auto">
             <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest">{{ filteredPolicies().length }} plans found</span>
          </div>
        </div>

        @if (isLoading()) {
          <div class="flex justify-center py-20">
            <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-[#8C1D40]"></div>
          </div>
        } @else {
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            @for (policy of filteredPolicies(); track policy.policyId) {
              <div
                [class.ring-4]="policy.policyId === suggestedPolicyId()"
                [class.ring-[#8C1D40]]="policy.policyId === suggestedPolicyId()"
                [class.bg-slate-50]="policy.policyId === suggestedPolicyId()"
                class="bg-white rounded-[2.5rem] border-2 border-slate-100 p-8 flex flex-col hover:shadow-2xl hover:-translate-y-2 transition-all duration-500 group relative">

                <!-- AI Recommended Badge -->
                @if (policy.policyId === suggestedPolicyId()) {
                  <div class="absolute -top-4 left-1/2 -translate-x-1/2 bg-[#8C1D40] text-white px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-widest shadow-xl flex items-center gap-2 z-10 animate-bounce">
                    <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M13 6a3 3 0 11-6 0 3 3 0 016 0zM18 8a2 2 0 11-4 0 2 2 0 014 0zM14 15a4 4 0 00-8 0v3h8v-3zM6 8a2 2 0 11-4 0 2 2 0 014 0zM16 18v-3a5.972 5.972 0 00-.75-2.906A3.005 3.005 0 0119 15v3h-3zM4.75 12.094A5.973 5.973 0 004 15v3H1v-3a3 3 0 013.75-2.906z" /></svg>
                    AI Recommended
                  </div>
                }

                <div class="mb-6 flex items-center justify-between">
                  <div class="w-14 h-14 rounded-2xl bg-slate-50 flex items-center justify-center group-hover:bg-[#8C1D40]/10 transition-colors">
                    <svg class="w-8 h-8 text-[#8C1D40]" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  </div>
                  <span class="px-4 py-1.5 rounded-full bg-slate-100 text-[#1F2937] text-[10px] font-black uppercase tracking-widest">{{ policy.domain }}</span>
                </div>

                <h3 class="text-2xl font-black text-[#1F2937] mb-3">{{ policy.policyName }}</h3>
                <p class="text-sm text-slate-500 font-medium mb-8 leading-relaxed">{{ policy.description }}</p>

                <div class="space-y-4 mb-8">
                  <div class="flex justify-between items-center pb-3 border-b border-slate-50">
                    <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest">Base Rate</span>
                    <span class="text-lg font-black text-[#1F2937]">{{ policy.baseRate }}%</span>
                  </div>
                  <div class="flex justify-between items-center pb-3 border-b border-slate-50">
                    <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest">Max Payout</span>
                    <span class="text-lg font-black text-[#8C1D40]">₹{{ policy.maxCoverageAmount | number }}</span>
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3 mb-10">
                  <div class="flex items-center gap-2 text-[10px] font-black uppercase tracking-tighter" [class.text-green-600]="policy.coversTheft" [class.text-slate-300]="!policy.coversTheft">
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M5 13l4 4L19 7" stroke-width="4"/></svg> Theft
                  </div>
                  <div class="flex items-center gap-2 text-[10px] font-black uppercase tracking-tighter" [class.text-green-600]="policy.coversWeather" [class.text-slate-300]="!policy.coversWeather">
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M5 13l4 4L19 7" stroke-width="4"/></svg> Weather
                  </div>
                  <div class="flex items-center gap-2 text-[10px] font-black uppercase tracking-tighter" [class.text-green-600]="policy.coversFire" [class.text-slate-300]="!policy.coversFire">
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M5 13l4 4L19 7" stroke-width="4"/></svg> Fire
                  </div>
                  <div class="flex items-center gap-2 text-[10px] font-black uppercase tracking-tighter" [class.text-green-600]="policy.coversCancelation" [class.text-slate-300]="!policy.coversCancelation">
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path d="M5 13l4 4L19 7" stroke-width="4"/></svg> Cancel
                  </div>
                </div>


              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: []
})
export class PolicyListComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly aiService = inject(AiService);

  readonly allPolicies = signal<any[]>([]);
  readonly selectedDomain = signal<string>('ALL');
  readonly isLoading = signal(true);
  readonly suggestedPolicyId = this.aiService.suggestedPolicyId;

  readonly filteredPolicies = computed(() => {
    const domain = this.selectedDomain();
    const policies = this.allPolicies();
    if (domain === 'ALL') return policies;
    return policies.filter(p => p.domain === domain);
  });

  ngOnInit(): void {
    this.http.get<any>('http://localhost:8080/policies/active').subscribe({
      next: (res) => {
        this.allPolicies.set(res.data ?? res ?? []);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  onDomainChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedDomain.set(value);
  }

  goBack(): void {
    this.router.navigate(['/customer-dashboard']);
  }
}

