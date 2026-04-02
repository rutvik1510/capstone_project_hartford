import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { NotificationDropdownComponent } from '../notifications/notification-dropdown.component';

@Component({
  standalone: true,
  selector: 'app-customer-dashboard',
  imports: [CommonModule, RouterModule, NotificationDropdownComponent, FormsModule],
  templateUrl: './customer-dashboard.component.html',
})
export class CustomerDashboardComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly customerEmail = computed(() => {
    return this.authService.getEmail() || 'Customer';
  });

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
