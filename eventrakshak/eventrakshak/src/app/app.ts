import { Component, inject, computed } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common'; // Add CommonModule
import { GeminiChatbotComponent } from './features/notifications/gemini-chatbot.component'; // Add Chatbot
import { AuthService } from './core/auth.service'; // Add AuthService

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, GeminiChatbotComponent], // Add imports
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly authService = inject(AuthService);

  readonly isCustomer = computed(() => {
    return this.authService.isLoggedIn() && this.authService.hasRole('ROLE_CUSTOMER');
  });
}
