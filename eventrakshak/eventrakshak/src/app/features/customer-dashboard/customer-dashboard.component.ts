import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { NotificationDropdownComponent } from '../notifications/notification-dropdown.component';
import { AiService } from './ai.service';

interface Message {
  text: string;
  sender: 'user' | 'agent';
}

@Component({
  standalone: true,
  selector: 'app-customer-dashboard',
  imports: [CommonModule, RouterModule, NotificationDropdownComponent, FormsModule],
  templateUrl: './customer-dashboard.component.html',
})
export class CustomerDashboardComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly aiService = inject(AiService);

  readonly customerEmail = computed(() => {
    const token = this.authService.getToken();
    if (!token) return 'Customer';
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload['sub'] ?? payload['email'] ?? 'Customer';
    } catch {
      return 'Customer';
    }
  });

  // Chatbot state
  readonly isChatOpen = signal(false);
  currentMessage = '';
  readonly chatHistory = signal<Message[]>([
    { text: 'Hello! I am your EventGuard assistant. How can I help you with your insurance today?', sender: 'agent' }
  ]);
  readonly isTyping = signal(false);

  ngOnInit(): void {
    this.loadChatHistory();
  }

  loadChatHistory(): void {
    this.aiService.getHistory().subscribe({
      next: (res: any) => {
        const dbMessages = res.data ?? [];
        if (dbMessages.length > 0) {
          const mappedMessages: Message[] = dbMessages.map((m: any) => ({
            text: m.message,
            sender: m.sender === 'USER' ? 'user' : 'agent'
          }));
          this.chatHistory.set(mappedMessages);
        }
      },
      error: () => console.error('Failed to load chat history')
    });
  }

  toggleChat(): void {
    this.isChatOpen.update(v => !v);
  }

  sendMessage(): void {
    const msg = this.currentMessage.trim();
    if (!msg) return;

    this.chatHistory.update(history => [...history, { text: msg, sender: 'user' }]);
    this.currentMessage = '';
    this.isTyping.set(true);

    this.aiService.sendMessage({ userQuery: msg }).subscribe({
      next: (res: any) => {
        const data = res.data;
        this.chatHistory.update(history => [...history, { text: data.message, sender: 'agent' }]);
        if (data.suggestedPolicyId) {
          this.aiService.suggestedPolicyId.set(data.suggestedPolicyId);
        }
        this.isTyping.set(false);
      },
      error: () => {
        this.chatHistory.update(history => [...history, { text: 'Sorry, I encountered an error. Please try again later.', sender: 'agent' }]);
        this.isTyping.set(false);
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
