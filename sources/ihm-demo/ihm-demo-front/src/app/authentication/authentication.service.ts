import { Injectable } from '@angular/core';
import {CookieService} from "angular2-cookie/core";
import {Router} from '@angular/router';
import { BehaviorSubject } from "rxjs/BehaviorSubject"

import { ResourcesService } from '../common/resources.service';

const LOGGED_IN = 'loggedIn';
const ADMIN_TENANT = 'admintenant';
const USER = 'user';

export class UserInformation {
  permissions : string[];
  userName : string;
  sessionTimeout: number;
  tokenCSRF : string;
}

@Injectable()
export class AuthenticationService {

  private loginState = new BehaviorSubject<boolean>(false);
  private userInformation : UserInformation;

  constructor(private cookies: CookieService, private resourceService: ResourcesService, private router : Router) {
    if (localStorage.getItem(USER)) {
      let userInfo = localStorage.getItem(USER);
      this.userInformation = JSON.parse(localStorage.getItem(USER));
    }
    if (localStorage.getItem(LOGGED_IN) === 'true') {
      this.loginState.next(true);
    }
  }

  loggedIn(user : UserInformation, tenantId : string) {
    localStorage.setItem(LOGGED_IN, 'true');
    localStorage.setItem('XSRF-TOKEN', user.tokenCSRF);
    this.userInformation = user;
    localStorage.setItem(USER, JSON.stringify(user));
    this.setTenant(tenantId);
    this.loginState.next(true);
  }

  logIn(id : string, password: string) {
    this.resourceService.removeSessionInfo();
    return this.resourceService.post('login', null, {"token":{"principal":id,"credentials":password}});
  }

  loggedOut() {
    localStorage.setItem(LOGGED_IN, 'false');
    this.loginState.next(false);
    localStorage.removeItem('XSRF-TOKEN');
    localStorage.removeItem(USER);
    this.resourceService.removeSessionInfo();
    this.router.navigate(['login']);
  }

  isLoggedIn() {
    return localStorage.getItem(LOGGED_IN) == 'true';
  }

  logOut() {
    this.resourceService.removeSessionInfo();
    return this.resourceService.post('logout', null, {});
  }

  getLoginState(): BehaviorSubject<boolean> {
    return this.loginState;
  }


  getTenants() {
    return this.resourceService.getTenants();
  }

  setTenant(tenantId : string) {
    this.resourceService.setTenant(tenantId);
  }

  getUserinformation() : UserInformation {
    return this.userInformation;
  }

  isAdmin() {
    // TODO find better check
    return this.checkUserPermission('format:create');
  }

  checkUserPermission(permission : string) : boolean {
    if (this.userInformation) {
      return this.userInformation.permissions.indexOf(permission) > -1;
    } else {
      return false;
    }
  }

  logInWithCertificat() {
    this.resourceService.removeSessionInfo();
    return this.resourceService.get('permissions');
  }

  getAuthenticationMode() {
    return this.resourceService.get('securemode');
  }

  storeTenantAdmin() {
    this.resourceService.get(ADMIN_TENANT).subscribe(
      (tenantAdmin : string) => {
        this.cookies.put(ADMIN_TENANT, tenantAdmin);
      },
      (error) => {
        console.log(error);
      }
    );
  }

  isTenantAdmin() {
    return this.cookies.get(ADMIN_TENANT) === this.resourceService.getTenant();
  }

  loggedInWithCertificat(tenantId : string) {
    localStorage.setItem(LOGGED_IN, 'true');
    this.loginState.next(true);
    this.setTenant(tenantId);
  }
}
