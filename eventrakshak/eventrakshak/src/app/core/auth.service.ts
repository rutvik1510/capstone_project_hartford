import { Injectable, signal, computed } from '@angular/core';
import { jwtDecode } from 'jwt-decode';

interface JwtPayload {
  sub?: string;
  roles?: string[];
  role?: string;
  exp?: number;
  fullName?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  
  // Directly initialize signals from localStorage
  readonly isLoggedIn = signal<boolean>(this.hasValidToken());
  readonly userName = signal<string>(this.getNameFromStore());

  private hasValidToken(): boolean {
    if (typeof window === 'undefined') return false;
    
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) return false;
    
    try {
      const decoded = jwtDecode<JwtPayload>(token);
      const isExpired = decoded.exp ? (decoded.exp * 1000) < Date.now() : false;
      if (isExpired) {
        this.clearSession();
        return false;
      }
      return true;
    } catch {
      this.clearSession();
      return false;
    }
  }

  private getNameFromStore(): string {
    if (typeof window === 'undefined') return '';
    return localStorage.getItem('user_name') || '';
  }

  login(token: string, name: string, email: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    localStorage.setItem('user_name', name);
    localStorage.setItem('user_email', email);
    this.isLoggedIn.set(true);
    this.userName.set(name);
  }

  logout(): void {
    this.clearSession();
    this.isLoggedIn.set(false);
    this.userName.set('');
  }

  private clearSession(): void {
    if (typeof window !== 'undefined') {
      localStorage.clear();
    }
  }

  getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRoles(): string[] {
    const token = this.getToken();
    if (!token) return [];
    
    try {
      console.log('DEBUG: Current Token:', token);
      const payload = jwtDecode<any>(token);
      console.log('DEBUG: JWT Payload:', payload);
      const roles: any[] = [];
      
      if (Array.isArray(payload.roles)) {
        roles.push(...payload.roles);
      } else if (payload.roles) {
        roles.push(payload.roles);
      } else if (payload.role) {
        roles.push(payload.role);
      }

      return roles.map(r => {
        // Handle if role is an object like {authority: 'ROLE_...'}
        const roleStr = (typeof r === 'object' && r.authority) ? r.authority : String(r);
        const u = roleStr.toUpperCase();
        return u.startsWith('ROLE_') ? u : `ROLE_${u}`;
      });
    } catch (e) {
      console.error('DEBUG: Error decoding roles:', e);
      return [];
    }
  }

  hasRole(expectedRole: string): boolean {
    if (!expectedRole) return true;
    const roles = this.getRoles();
    const normalized = expectedRole.toUpperCase();
    const withPrefix = normalized.startsWith('ROLE_') ? normalized : `ROLE_${normalized}`;
    const result = roles.includes(withPrefix);
    console.log(`DEBUG: hasRole('${expectedRole}')? Result: ${result}. User roles:`, roles);
    return result;
  }

  // Helper for primary role (routing)
  getPrimaryRole(): string | null {
    const roles = this.getRoles();
    if (roles.length === 0) return null;
    return roles[0].replace(/^ROLE_/i, '').toUpperCase();
  }

  getEmail(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const decoded = jwtDecode<JwtPayload>(token);
      return decoded.sub ?? null;
    } catch {
      return null;
    }
  }
}
