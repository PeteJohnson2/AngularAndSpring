/*
 *    Copyright 2016 Sven Loesekann

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
import { Component, OnInit, Inject, DestroyRef, inject } from "@angular/core";
import { QuoteoverviewComponent } from "../quoteoverview/quoteoverview.component";
import { MatDialogRef, MAT_DIALOG_DATA } from "@angular/material/dialog";
import { MyuserService } from "../../services/myuser.service";
import { MyUser } from "../../common/my-user";
import {
  FormGroup,
  FormBuilder,
  Validators,
  AbstractControlOptions,
} from "@angular/forms";
import { TokenService } from "ngx-simple-charts/base-service";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

enum FormFields {
  username = "username",
  password = "password",
  password2 = "password2",
  email = "email",
}

@Component({
    selector: "app-login",
    templateUrl: "./login.component.html",
    styleUrls: ["./login.component.scss"],
    standalone: false
})
export class LoginComponent implements OnInit {
  protected signinForm: FormGroup;
  protected loginForm: FormGroup;
  protected loginFailed = false;
  protected signinFailed = false;
  protected pwMatching = true;
  protected formFields = FormFields;
  protected waitingForResponse = false;
  private user = new MyUser();
  private readonly destroy: DestroyRef = inject(DestroyRef);

  constructor(
    public dialogRef: MatDialogRef<QuoteoverviewComponent>,
    private tokenService: TokenService,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private myuserService: MyuserService,
    fb: FormBuilder,
  ) {
    this.signinForm = fb.group(
      {
        [FormFields.username]: [
          "",
          [Validators.required, Validators.minLength(4)],
        ],
        [FormFields.password]: [
          "",
          [Validators.required, Validators.minLength(4)],
        ],
        [FormFields.password2]: [
          "",
          [Validators.required, Validators.minLength(4)],
        ],
        [FormFields.email]: ["", Validators.required],
      },
      {
        validators: this.validate.bind(this),
      } as AbstractControlOptions,
    );
    this.loginForm = fb.group({
      [FormFields.username]: [
        "",
        [Validators.required, Validators.minLength(4)],
      ],
      [FormFields.password]: [
        "",
        [Validators.required, Validators.minLength(4)],
      ],
    });
  }

  ngOnInit() {}

  validate(group: FormGroup) {
    if (
      group.get(FormFields.password).touched ||
      group.get(FormFields.password2).touched
    ) {
      this.pwMatching =
        group.get(FormFields.password).value ===
          group.get(FormFields.password2).value &&
        group.get(FormFields.password).value !== "";
      if (!this.pwMatching) {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        group.get(FormFields.password).setErrors({ MatchPassword: true });
        // eslint-disable-next-line @typescript-eslint/naming-convention
        group.get(FormFields.password2).setErrors({ MatchPassword: true });
      } else {
        group.get(FormFields.password).setErrors(null);
        group.get(FormFields.password2).setErrors(null);
      }
    }
    return this.pwMatching;
  }

  onSigninClick(): void {
    const myUser = new MyUser();
    myUser.userId = this.signinForm.get(FormFields.username).value;
    myUser.password = this.signinForm.get(FormFields.password).value;
    myUser.email = this.signinForm.get(FormFields.email).value;
    //      console.log(this.signinForm);
    //      console.log(myUser);
    this.waitingForResponse = true;
    this.myuserService
      .postSignin(myUser)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (us) => this.signin(us),
        error: (err) => console.log(err),
      });
  }

  onLoginClick(): void {
    const myUser = new MyUser();
    myUser.userId = this.loginForm.get(FormFields.username).value;
    myUser.password = this.loginForm.get(FormFields.password).value;
    //      console.log(myUser);
    this.waitingForResponse = true;
    this.myuserService
      .postLogin(myUser)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (us) => this.login(us),
        error: (err) => console.log(err),
      });
  }

  signin(us: MyUser): void {
    this.user = us;
    this.data.loggedIn = !!us?.token;
    this.waitingForResponse = false;
    if (this.user.userId !== null) {
      this.signinFailed = false;
      this.dialogRef.close(this.data.loggedIn);
    } else {
      this.signinFailed = true;
    }
  }

  login(us: MyUser): void {
    this.user = us;
    this.data.loggedIn = !!us?.token;
    this.waitingForResponse = false;
    if (this.user.userId !== null) {
      this.loginFailed = false;
      this.tokenService.token = us.token;
      this.tokenService.userId = us.userId;
      this.dialogRef.close(this.data.loggedIn);
    } else {
      this.loginFailed = true;
    }
  }

  onCancelClick(): void {
    this.dialogRef.close();
  }
}
