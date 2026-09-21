"""Real frozen-model integration plus failure and calendar boundary tests."""
from copy import deepcopy
import json
from pathlib import Path
import threading
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import pytest
from src.tuntun_app_vnext import SCHEMA_VERSION
from src.tuntun_app_vnext.inputs import normalize, month_age
from src.tuntun_app_vnext.service import AppService, rank_display
from src.tuntun_app_vnext.schema import validate_response
from src.tuntun_app_vnext.server import make_server, strict_json
from src.tuntun_app_vnext.release import CORE_PIN
from src.tuntun_peer.bundle import load_bundle

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT/'core_bundle'
if not CORE.exists():
    CORE = ROOT/'artifacts/production_candidates/tuntun_peer_v0_1_20260907_r2/bundle'
BASE = dict(birthYear=1991, birthMonth=1, referenceDate='2026-09-09', sex='male',
            pregnancyStatus='not_applicable', heightCm=170, weightKg=64,
            strengthWeeklyCount=4, strengthFrequencyUnit='days', strengthIntensity='moderate',
            aerobicLowMinutes=60, aerobicModerateMinutes=120, aerobicVigorousMinutes=0,
            bedtime=None, wakeTime=None, inputRevision='test-64')

@pytest.fixture(scope='module')
def service():
    return AppService(load_bundle(CORE, CORE_PIN))

@pytest.mark.parametrize('year,month,day,expected', [
    (1991,1,'2026-09-09',[35,35]), (1991,10,'2026-09-09',[34,34]),
    (1991,9,'2026-09-01',[34,35]), (1991,9,'2026-09-29',[34,35]),
    (1991,9,'2026-09-30',[35,35]), (1991,2,'2024-02-28',[32,33]),
    (1991,2,'2024-02-29',[33,33]), (1991,2,'2025-02-28',[34,34]),
    (1991,12,'2026-01-01',[34,34]), (2007,9,'2026-09-09',[18,19])])
def test_year_month(year,month,day,expected):
    assert month_age(year,month,day)['possibleAgeYears'] == expected

@pytest.mark.parametrize('changes', [dict(birthMonth=0),dict(birthMonth=13),dict(birthYear=True),
    dict(birthYear=2027),dict(referenceDate='2026-02-30'),dict(referenceDate='20260909'),
    dict(birthDate='1991-01-01'),dict(weightKg=float('nan')),dict(weightKg=True),dict(weightKg=251),
    dict(sex='other'),dict(pregnancyStatus='pregnant'),dict(strengthWeeklyCount=8),
    dict(strengthWeeklyCount=1.5),dict(strengthIntensity='maximum'),dict(strengthFrequencyUnit='week'),
    dict(strengthWeeklyCount=0),dict(bedtime='24:00'),dict(wakeTime='7:30'),
    dict(aerobicLowMinutes=10000,aerobicModerateMinutes=100),dict(inputRevision='')])
def test_invalid_inputs(changes):
    with pytest.raises(ValueError): normalize(BASE | changes)

def test_frequency_missing_zero_and_topcode():
    assert normalize(BASE | {'strengthFrequencyUnit':'sessions'})['canonical']['features']['strength_days_week'] is None
    assert normalize(BASE | {'strengthFrequencyUnit':None})['strengthReason'] == 'STRENGTH_DAYS_UNCONFIRMED'
    assert normalize(BASE | {'strengthWeeklyCount':0,'strengthIntensity':None,'strengthFrequencyUnit':None})['canonical']['features']['strength_days_week'] == 0
    assert normalize(BASE | {'strengthWeeklyCount':7})['canonical']['features']['strength_days_week'] == 5

def test_aerobic_missing_is_not_zero():
    assert normalize(BASE)['canonical']['features']['leisure_aerobic_moderate_equivalent_min_week'] == 120
    assert normalize(BASE | {'aerobicVigorousMinutes':None})['canonical']['features']['leisure_aerobic_moderate_equivalent_min_week'] is None
    assert normalize(BASE | {'aerobicVigorousMinutes':30})['canonical']['features']['leisure_aerobic_moderate_equivalent_min_week'] == 180

def test_sleep_interval():
    assert normalize(BASE | dict(bedtime='23:00',wakeTime='07:00'))['habitContext']['bedToWakeIntervalMinutes'] == 480
    assert normalize(BASE | dict(bedtime='07:00',wakeTime='15:00'))['habitContext']['bedToWakeIntervalMinutes'] == 480
    assert normalize(BASE | dict(bedtime='07:00',wakeTime='07:00'))['habitContext']['bedToWakeStatus'] == 'ambiguous_same_time'

def scores(result):
    return [(c['absoluteReferenceScore'],c['peerPercentile']) for c in result['components']]

def test_real_weight_regression(service):
    a,b = service.score(BASE),service.score(BASE | dict(weightKg=164,inputRevision='test-164'))
    assert a['peerCompositeScore'] == pytest.approx(75.5858783473669)
    assert b['peerCompositeScore'] == pytest.approx(21.081988747354227)
    assert [c['rankDisplay']['rankApprox'] for c in a['components']] == [21,34,19,24]
    assert [c['rankDisplay']['rankApprox'] for c in b['components']] == [100,98,94,24]
    assert all(x != y for x,y in zip(scores(a)[:3],scores(b)[:3]))
    assert scores(a)[3] == scores(b)[3]
    assert a == service.score(BASE)
    assert a['isMock'] is False and a['isSynthetic'] is False
    assert b['inputRevision'] == 'test-164'

def test_context_only_invariance(service):
    a=service.score(BASE)
    b=service.score(BASE | dict(aerobicLowMinutes=600,strengthIntensity='hard',bedtime='23:00',wakeTime='07:00'))
    assert scores(a) == scores(b)
    assert a['peerCompositeScore'] == b['peerCompositeScore']
    assert a['habitContext'] != b['habitContext']

@pytest.mark.parametrize('changes', [dict(pregnancyStatus='unknown'),
    dict(sex='female',pregnancyStatus='pregnant'),dict(heightCm=None),dict(weightKg=None),
    dict(strengthFrequencyUnit='sessions'),dict(birthYear=2007,birthMonth=9)])
def test_partial_health_not_fake(service,changes):
    out=service.score(BASE | changes)
    assert all(not c['available'] for c in out['components'][:3])
    assert all(c['rankDisplay']['rankApprox'] is None for c in out['components'][:3])

def test_no_score(service):
    out=service.score(BASE | dict(aerobicModerateMinutes=None,strengthWeeklyCount=None,strengthIntensity=None))
    assert out['peerCompositeScore'] is None and not out['scoreAvailable']

def test_all_tie_rank():
    assert rank_display(dict(available=True,peerPercentile=50,percentileRange=[0,100],tieMassPercent=100)) == dict(rankApprox=50,rankRange=[1,100],text='또래 100명 중 약 50등',tieNotice='동점자가 있어 대략적인 위치입니다.')

@pytest.mark.parametrize('key,value',[('availableComponentCount',0),('peerCompositeScore',80),('isMock',True),('isSynthetic',True)])
def test_dto_corruption(service,key,value):
    out=service.score(BASE);out[key]=value
    with pytest.raises(ValueError): validate_response(out)

def test_rank_corruption(service):
    out=service.score(BASE);out['components'][0]['rankDisplay']['rankApprox']=1
    with pytest.raises(ValueError): validate_response(out)

@pytest.mark.parametrize('body',['{"a":1,"a":2}','{"a":NaN}','{"a":Infinity}'])
def test_strict_json(body):
    with pytest.raises(ValueError):strict_json(body)

def post(server,data,schema=SCHEMA_VERSION):
    req=Request(f'http://127.0.0.1:{server.server_port}/score/peer/v2',data=json.dumps(data).encode(),headers={'Content-Type':'application/json','X-Tuntun-Schema':schema})
    try:
        with urlopen(req,timeout=20) as response:return response.status,json.load(response)
    except HTTPError as exc:return exc.code,json.load(exc)

def test_real_http(service):
    server=make_server(service);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        status,out=post(server,BASE)
        assert status == 200 and out == service.score(BASE)
        status,out=post(server,BASE | {'weightKg':164})
        assert status == 200 and out['peerCompositeScore'] == pytest.approx(21.081988747354227)
        assert post(server,BASE,'old')[0] == 409
        status,out=post(server,BASE | {'weightKg':-1})
        assert status == 422 and out['scoreAvailable'] is False and 'peerCompositeScore' not in out
    finally:server.shutdown();server.server_close();thread.join(5)

def test_model_failure_is_503():
    class Broken:
        def score(self,_):raise ValueError('sensitive internal exception')
    server=make_server(AppService(Broken()));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    try:
        status,out=post(server,BASE)
        assert status == 503 and out == {'error':'MODEL_INFERENCE_FAILED','scoreAvailable':False}
    finally:server.shutdown();server.server_close();thread.join(5)
