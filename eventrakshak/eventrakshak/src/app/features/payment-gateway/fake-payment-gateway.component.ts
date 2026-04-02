import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MySubscriptionsService } from '../my-subscriptions/my-subscriptions.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-fake-payment-gateway',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-slate-50 flex items-center justify-center p-4 font-sans">
      <div class="max-w-md w-full bg-white rounded-[2.5rem] shadow-[0_20px_50px_rgba(0,0,0,0.1)] overflow-hidden border border-slate-100">
        
        <!-- Premium Header -->
        <div class="bg-[#1F2937] p-10 text-white text-center relative overflow-hidden">
          <div class="absolute top-0 right-0 -mr-8 -mt-8 w-32 h-32 bg-[#8C1D40] opacity-20 rounded-full blur-2xl"></div>
          <div class="absolute bottom-0 left-0 -ml-8 -mb-8 w-32 h-32 bg-indigo-500 opacity-20 rounded-full blur-2xl"></div>
          
          <div class="w-20 h-20 bg-white/10 backdrop-blur-md rounded-3xl flex items-center justify-center mx-auto mb-6 border border-white/20 shadow-2xl">
            <svg class="w-10 h-10 text-white" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z" />
            </svg>
          </div>
          <h1 class="text-3xl font-black tracking-tight mb-1">Secure Checkout</h1>
          <p class="text-white/60 text-xs font-bold uppercase tracking-[0.2em]">EventGuard Payments</p>
        </div>

        <div class="p-10">
          <!-- Order Summary -->
          <div class="bg-slate-50 rounded-3xl p-8 mb-8 border border-slate-100 flex flex-col items-center">
            <p class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-3">Total Amount to Pay</p>
            <div class="flex items-baseline gap-2">
              <span class="text-5xl font-black text-slate-800 tracking-tighter">₹{{ amount() | number }}</span>
              <span class="text-slate-400 font-bold text-sm">INR</span>
            </div>
          </div>

          <!-- Card Details Form -->
          <div class="space-y-5 mb-8">
            <div>
              <label class="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 mb-2 block">Card Number</label>
              <div class="relative">
                <input type="text" placeholder="XXXX XXXX XXXX XXXX" 
                       class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-bold text-slate-800 outline-none focus:ring-2 focus:ring-[#8C1D40] focus:border-transparent transition-all">
                <div class="absolute right-4 top-1/2 -translate-y-1/2 flex gap-1 opacity-40">
                  <div class="w-6 h-4 bg-slate-300 rounded"></div>
                  <div class="w-6 h-4 bg-slate-400 rounded"></div>
                </div>
              </div>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 mb-2 block">Expiry</label>
                <input type="text" placeholder="MM / YY" 
                       class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-bold text-slate-800 outline-none focus:ring-2 focus:ring-[#8C1D40] focus:border-transparent transition-all">
              </div>
              <div>
                <label class="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 mb-2 block">CVV</label>
                <input type="password" placeholder="***" 
                       class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-bold text-slate-800 outline-none focus:ring-2 focus:ring-[#8C1D40] focus:border-transparent transition-all">
              </div>
            </div>
          </div>

          @if (errorMessage()) {
            <div class="mb-6 p-4 rounded-2xl bg-red-50 border border-red-100 text-red-600 text-[10px] font-black uppercase tracking-wider text-center">
              ⚠️ {{ errorMessage() }}
            </div>
          }

          <!-- Pay Button -->
          <button (click)="processPayment()" [disabled]="isProcessing()"
            class="w-full py-5 rounded-2xl bg-[#1F2937] hover:bg-[#8C1D40] text-white font-black uppercase tracking-[0.2em] text-xs transition-all duration-300 shadow-[0_15px_30px_rgba(31,41,51,0.3)] active:scale-95 disabled:opacity-50 disabled:pointer-events-none group">
            @if (isProcessing()) {
              <div class="flex items-center justify-center gap-3">
                <div class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                <span>Verifying...</span>
              </div>
            } @else {
              <span class="group-hover:translate-x-1 transition-transform inline-block">Confirm & Pay</span>
            }
          </button>

          <!-- Security Badges -->
          <div class="mt-8 pt-8 border-t border-slate-100 flex items-center justify-between">
            <div class="flex flex-col">
              <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest">Security</span>
              <span class="text-[10px] font-bold text-green-600 uppercase">256-bit AES</span>
            </div>
            <div class="flex gap-4 grayscale opacity-30">
               <span class="font-black italic text-sm">VISA</span>
               <span class="font-black italic text-sm">MasterCard</span>
            </div>
          </div>

          <p class="text-center text-[9px] text-slate-300 font-bold mt-8 uppercase tracking-[0.3em]">
            PCI DSS Compliant Infrastructure
          </p>
        </div>
      </div>
    </div>
  `,
})
export class FakePaymentGatewayComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(MySubscriptionsService);

  readonly subscriptionId = signal<number>(0);
  readonly amount = signal<number>(0);
  readonly isProcessing = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const sid = this.route.snapshot.queryParamMap.get('subscriptionId');
    const amt = this.route.snapshot.queryParamMap.get('amount');
    
    if (!sid) {
      this.router.navigate(['/customer-dashboard']);
      return;
    }

    this.subscriptionId.set(Number(sid));
    this.amount.set(Number(amt || 0));
  }

  processPayment(): void {
    this.isProcessing.set(true);
    this.errorMessage.set(null);

    // Simulate network delay for "gateways"
    setTimeout(() => {
      this.service.payPremium(this.subscriptionId()).subscribe({
        next: () => {
          this.service.reloadSubscriptions(); // Force fresh data
          this.router.navigate(['/my-subscriptions'], { 
            queryParams: { paid: 'true', id: this.subscriptionId() } 
          });
        },
        error: (err) => {
          this.isProcessing.set(false);
          this.errorMessage.set(err?.error?.message ?? 'Payment rejected by bank.');
        }
      });
    }, 2500);
  }
}
