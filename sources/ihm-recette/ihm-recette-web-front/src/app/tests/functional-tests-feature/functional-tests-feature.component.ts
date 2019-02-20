import {Component, OnDestroy} from '@angular/core';
import {Title} from '@angular/platform-browser';

import {BreadcrumbElement, BreadcrumbService} from '../../common/breadcrumb.service';
import {PageComponent} from '../../common/page/page-component';
import {Subscription} from 'rxjs/Subscription';
import {FunctionalTestsFeatureService} from '../functional-tests-feature.service';


const breadcrumb: BreadcrumbElement[] = [
  {label: 'Tests', routerLink: ''},
  {label: 'Test tnr', routerLink: 'tests/testFeature'}
];

@Component({
  selector: 'vitam-functional-tests-feature',
  templateUrl: './functional-tests-feature.component.html',
  styleUrls: ['./functional-tests-feature.component.css'],

})

export class FunctionalTestsFeatureComponent extends PageComponent {

  updateOk: boolean;
  updateKo: boolean;
  branch: string;
  branches: Array<string>;
  featureText: string;
  requestResponse: String;
  synchroResponse: String;

  constructor(public breadcrumbService: BreadcrumbService, public service: FunctionalTestsFeatureService, public titleService: Title) {
    super('Test tnr', breadcrumb, titleService, breadcrumbService);

  }

  sendRequest() {
    this.requestResponse = '';

    this.service.launchFeature(this.featureText).subscribe(
      (response) => {
        this.requestResponse = JSON.stringify(response, null, 2);
      }, (error) => {
        this.requestResponse = JSON.stringify(error, null, 2);

      });
  };

  syncWithBranch() {
    this.service.syncWithBranch(this.branch).subscribe(
      () => this.updateOk = true,
      () => this.updateKo = true
    );
  };


  pageOnInit(): Subscription {
    this.service.getAllBranches().subscribe(data =>  {
      this.branches = data;
    });

    return null;
  };


}
