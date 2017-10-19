import { Injectable } from '@angular/core';
import {ResourcesService} from "../common/resources.service";
import {Headers, Response} from "@angular/http";
import {Observable} from "rxjs/Observable";
import 'rxjs/add/observable/of';
import {VitamResponse} from "../common/utils/response";

@Injectable()
export class LogbookService {
  LOGBOOK_API = 'logbook/operations';
  REPORT_DOWNLOAD_API = 'rules/report/download';

  constructor(private resourceService: ResourcesService) { }

  getResults(body: any, offset: number = 0): Observable<VitamResponse> {

    const headers = new Headers();
    headers.append('X-Limit', '125');
    headers.append('X-Offset', '' + offset);

    return this.resourceService.post(this.LOGBOOK_API, headers, body)
        .map((res: Response) => res.json());
  }

  downloadReport(objectId) {
    this.resourceService.get(`${this.REPORT_DOWNLOAD_API}/${objectId}`)
        .subscribe(
            (response) => {
              const a = document.createElement('a');
              document.body.appendChild(a);

              a.href = URL.createObjectURL(new Blob([response.text()], {type: 'application/xml'}));

              if (response.headers.get('content-disposition') !== undefined && response.headers.get('content-disposition') !== null) {
                a.download = response.headers.get('content-disposition').split('filename=')[1];
                a.click();
              }
            }
        );
  }

}
