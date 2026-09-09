"""Python standard-library client; can run in the recipient's Python 3.13 server env."""
import argparse
import json
import math
import os
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

SCHEMA='tuntun-app-vnext-v0.2'

def check(url,examples,token=None):
    def send(body,schema=SCHEMA):
        headers={'Content-Type':'application/json','X-Tuntun-Schema':schema}
        if token:headers['Authorization']='Bearer '+token
        request=Request(url,data=json.dumps(body).encode('utf-8'),headers=headers)
        try:
            with urlopen(request,timeout=30) as r:return r.status,json.load(r)
        except HTTPError as e:return e.code,json.load(e)
    base=json.loads((examples/'base_64kg.json').read_text(encoding='utf-8'))
    heavy=json.loads((examples/'weight_164kg.json').read_text(encoding='utf-8'))
    outcomes=[]
    def require(condition,name):
        if not condition:raise ValueError(name)
        outcomes.append(name)
    responses=[]
    for name,body in [('base_64kg',base),('weight_164kg',heavy)]:
        status,out=send(body)
        expected=json.loads((examples/(name+'.expected.json')).read_text(encoding='utf-8'))
        require(status==200 and out.get('schemaVersion')==SCHEMA,name+'_schema')
        require(out.get('isMock') is False and out.get('isSynthetic') is False,name+'_real_flags')
        require(out.get('inputRevision')==body['inputRevision'],name+'_revision')
        require(math.isclose(out['peerCompositeScore'],expected['peerCompositeScore'],abs_tol=1e-8),name+'_composite')
        require(out['compositeDisplay']==expected['compositeDisplay'],name+'_composite_display')
        require([c['componentKey'] for c in out['components']]==[c['componentKey'] for c in expected['components']],name+'_component_keys')
        for got,want in zip(out['components'],expected['components']):
            require(got['rankDisplay']==want['rankDisplay'],name+'_'+got['componentKey']+'_rank')
            require(math.isclose(got['absoluteReferenceScore'],want['absoluteReferenceScore'],abs_tol=1e-8),name+'_'+got['componentKey']+'_absolute')
        responses.append(out)
    require(responses[0]['peerCompositeScore']!=responses[1]['peerCompositeScore'],'weight_change_recalculated')
    status,again=send(base)
    require(status==200 and again==responses[0],'repeat_deterministic_no_stale_response')
    context=base | dict(aerobicLowMinutes=600,strengthIntensity='hard',bedtime='23:00',wakeTime='07:00',inputRevision='context-check')
    status,out=send(context)
    require(status==200 and out['peerCompositeScore']==responses[0]['peerCompositeScore'] and out['habitContext']['bedToWakeIntervalMinutes']==480 and out['habitContext']['lowIntensityMinutes']==600 and out['habitContext']['strengthIntensity']=='hard' and out['inputRevision']=='context-check','context_only_no_score_bonus')
    status,out=send(base | {'weightKg':-1})
    require(status==422 and out.get('scoreAvailable') is False and 'peerCompositeScore' not in out,'invalid_input_no_mock')
    require(send(base,'old')[0]==409,'schema_mismatch_rejected')
    return {'status':'PASS','checks':len(outcomes),'passed':outcomes,'actualAppUiVerified':False,'productionApproved':False}

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--url',default='http://127.0.0.1:8766/score/peer/v2')
    p.add_argument('--examples',type=Path,default=Path(__file__).resolve().parents[1]/'examples')
    p.add_argument('--token-env',help='Optional bearer token environment variable name; never printed')
    p.add_argument('--receipt',type=Path)
    a=p.parse_args()
    token=os.environ[a.token_env] if a.token_env else None
    try:receipt=check(a.url,a.examples,token)
    except Exception as exc:
        receipt={'status':'FAIL','errorType':type(exc).__name__,'actualAppUiVerified':False}
    text=json.dumps(receipt,ensure_ascii=False,indent=2)
    if a.receipt:a.receipt.write_text(text+'\n',encoding='utf-8')
    print(text)
    if receipt['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
