"""Reproducible development-only selection; six-column machine input export."""
import argparse,csv,hashlib,json,random
from collections import Counter,defaultdict
from pathlib import Path

SOURCE_PIN='f6677ef29ab58d848b9341cf5f22c05cf9f75738b5be8edc67dd1592cebf03ba'
FEATURES=['age_years','sex_code','height_cm','weight_kg','leisure_aerobic_moderate_equivalent_min_week','strength_days_week']
SEED=20260911
N=300

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def select(source,output):
    if sha(source)!=SOURCE_PIN: raise ValueError('SOURCE_DOES_NOT_MATCH_FROZEN_TRAINING_INPUT')
    with source.open(encoding='utf-8-sig',newline='') as f: rows=list(csv.DictReader(f))
    assert len(rows)==16403
    assert {int(r['source_year']) for r in rows}=={2019,2020,2021}
    assert len({(r['source_year'],r['participant_id']) for r in rows})==len(rows)
    strata=defaultdict(list)
    for i,r in enumerate(rows):
        age=float(r['age_years']); sex=int(float(r['sex_code']))
        assert 19<=age<=80 and age.is_integer() and sex in (1,2)
        strata[(int(r['source_year']),sex,'19-39' if age<40 else '40-64' if age<65 else '65+')].append(i)
    quotas={k:len(v)*N//len(rows) for k,v in strata.items()}
    extras=N-sum(quotas.values())
    order=sorted(strata,key=lambda k:(-(len(strata[k])*N%len(rows)),k))
    for k in order[:extras]: quotas[k]+=1
    rng=random.Random(SEED)
    selected=[]
    for k in sorted(strata): selected.extend(rng.sample(strata[k],quotas[k]))
    rng.shuffle(selected)
    assert len(selected)==N and len(set(selected))==N
    output.mkdir(parents=True,exist_ok=True)
    values=[]
    for i in selected:
        r=rows[i]; v=[float(r[k]) for k in FEATURES]
        for j in (0,1,5): assert v[j].is_integer(); v[j]=int(v[j])
        values.append(v)
    with (output/'background_300.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.writer(f,lineterminator='\n'); writer.writerow(FEATURES); writer.writerows(values)
    receipt={'backgroundVersion':'waist-development-proportional300-seed20260911-v0.1',
        'rows':N,'columns':FEATURES,'sourceRows':len(rows),'sourceYears':[2019,2020,2021],
        'sourceFile':'development_v0_2/waist/development_2019_2021.csv','sourceSha256':SOURCE_PIN,
        'selection':'proportional stratified without replacement; largest remainder quotas; sorted strata; Python random.Random.sample then shuffle',
        'stratification':['source_year','sex_code','age_band_19_39_40_64_65_plus'],
        'seed':SEED,'samplingWeightsUsed':False,'labelsUsedForSelection':False,
        'upstreamEligibility':'frozen waist task cohort: waist_target_eligible, observed target, complete six inputs',
        'isFinalTestData':False,'years2022_2023_2024Read':False,
        'identifiersExported':False,'labelsExported':False,'synthetic':False,
        'sourceRowPositionsExported':False,'sourceOrderPreservedInOutput':False,
        'exportSha256':sha(output/'background_300.csv'),
        'jointRowsPreserved':True,'featureValuesModified':False,'missingImputation':False,
        'duplicateFeatureRows':N-len({tuple(v) for v in values}),
        'strata':[{'year':k[0],'sexCode':k[1],'ageBand':k[2],'sourceN':len(strata[k]),'selectedN':quotas[k]} for k in sorted(strata)]}
    (output/'selection_receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'PASS','rows':N,'strata':len(strata),'sourceRows':len(rows),'exportSha256':receipt['exportSha256']}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();select(a.source,a.output)
