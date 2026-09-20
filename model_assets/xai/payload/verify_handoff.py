"""Validate this handoff locally without original development data or API calls."""
import argparse,hashlib,importlib.metadata,json,time
from pathlib import Path
import numpy as np
import pandas as pd
from waist_cm import WaistCM,FEATURES,RELEASE_PIN

def read(p): return json.loads(p.read_text(encoding='utf-8'))
def save(p,v): p.write_text(json.dumps(v,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def equal(a,b):
    if isinstance(a,dict):
        assert a.keys()==b.keys()
        for k in a: equal(a[k],b[k])
    elif isinstance(a,list):
        assert len(a)==len(b)
        for x,y in zip(a,b): equal(x,y)
    elif isinstance(a,(float,int)) and not isinstance(a,bool): np.testing.assert_allclose(a,b,rtol=1e-9,atol=1e-9)
    else: assert a==b

def main():
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=Path(__file__).resolve().parent)
    p.add_argument('--output',type=Path,required=True); args=p.parse_args(); root=args.root.resolve()
    checks={}; began=time.perf_counter(); m=WaistCM(root/'01_inference')
    bg_path=root/'02_background/background_300.csv'; bg=pd.read_csv(bg_path)
    receipt=read(root/'02_background/selection_receipt.json')
    assert hashlib.sha256(bg_path.read_bytes()).hexdigest()==receipt['exportSha256']
    assert list(bg.columns)==FEATURES and len(bg)==300 and bg.notna().all().all()
    assert receipt['sourceYears']==[2019,2020,2021] and receipt['isFinalTestData'] is False
    assert sum(x['selectedN'] for x in receipt['strata'])==300
    prepared=m.prepare(bg); assert np.array_equal(bg.to_numpy(),prepared.to_numpy())
    assert list(m.tree.feature_names_in_)==FEATURES
    np.testing.assert_array_equal(m.predict_waist_cm(bg,pregnancy_status='nonpregnant'),m.tree.predict(prepared))
    checks['background_300_schema_and_pipeline_parity']='PASS'
    samples=pd.read_csv(root/'03_samples/waist_cm_inputs_5.csv')
    preds=m.predict_waist_cm(samples,pregnancy_status='nonpregnant')
    np.testing.assert_array_equal(preds,m.predictor.waist.predict(m.prepare(samples)))
    # Confirm age 90 is top-coded to 80 and not extrapolated at 90.
    capped=samples.iloc[[4]].copy(); capped['age_years']=80
    np.testing.assert_array_equal(preds[4:5],m.predict_waist_cm(capped,pregnancy_status='nonpregnant'))
    # Confirm the exact cm in original score path is used, before residual-CDF conversion.
    from src.tuntun_peer.bundle import legacy
    for row,pred in zip(samples.to_dict('records'),preds):
        score=m.predictor.score(row,pregnancy_status='nonpregnant',activity_window_end='2026-09-11')
        physical=100*(1-legacy.physical_probability(pred,row['age_years'],row['sex_code'],m.predictor.reference))
        np.testing.assert_allclose(score['physicalScore'],physical,rtol=0,atol=1e-10)
    checks['synthetic_cm_samples_5_original_path_and_age_cap']='PASS'
    negative=[]
    for column,value in [('age_years',18),('age_years',121),('age_years',35.5),('sex_code',3),
                         ('height_cm',99),('weight_kg',251),('weight_kg',None),
                         ('strength_days_week',6),('leisure_aerobic_moderate_equivalent_min_week',-1),
                         ('height_cm',float('inf'))]:
        bad=samples.iloc[[0]].copy(); bad[column]=value
        negative.append((column+':'+str(value),bad,'nonpregnant'))
    bad=samples.iloc[[0]].astype(object);bad.loc[0,'age_years']='35';negative.append(('numeric_string',bad,'nonpregnant'))
    bad=samples.iloc[[0]].astype(object);bad.loc[0,'age_years']=True;negative.append(('bool',bad,'nonpregnant'))
    negative.extend([('column_order',samples[list(reversed(FEATURES))],'nonpregnant'),
                     ('empty',samples.iloc[:0],'nonpregnant'),('pregnant',samples,'pregnant'),('unknown',samples,'unknown')])
    for label,bad,status in negative:
        try: m.predict_waist_cm(bad,pregnancy_status=status)
        except ValueError: pass
        else: raise AssertionError('Expected rejection: '+label)
    checks['invalid_inputs_rejected']=len(negative)
    original=[]
    for name in ['base_64kg','weight_164kg','no_exercise','missing_strength_days','no_score']:
        equal(m.app.score(read(root/f'01_inference/examples/{name}.json')),read(root/f'01_inference/examples/{name}.expected.json'))
        original.append(name)
    checks['original_app_samples_unchanged']=original
    started=time.perf_counter()
    result=m.explain_waist_cm(samples,bg,pregnancy_status='nonpregnant')
    shap_seconds=time.perf_counter()-started
    expected_path=root/'03_samples/waist_cm.expected.json'
    if expected_path.exists(): equal(preds.tolist(),read(expected_path)['predictionCm'])
    save(args.output,{'status':'PASS','checks':checks,'environment':{k:importlib.metadata.version(k) for k in ['numpy','pandas','scipy','scikit-learn','joblib','threadpoolctl','shap','numba','llvmlite']},
        'releaseManifestSha256':RELEASE_PIN,'backgroundSha256':receipt['exportSha256'],
        'predictionCm':preds.tolist(),'shap':result,
        'shapSecondsFiveSamples':shap_seconds,'totalSeconds':time.perf_counter()-began,
        'retrained':False,'llmApiExecuted':False,'clinicalValidation':False,'productionApproved':False})
    print(json.dumps({'status':'PASS','predictionCm':preds.tolist(),'backgroundRows':len(bg),
        'shapMaxAbsErrorCm':result['maxAbsAdditivityErrorCm'],'shapSeconds':shap_seconds,'negativeTests':len(negative)}),flush=True)

if __name__=='__main__': main()
