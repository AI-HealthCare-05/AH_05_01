"""Hash-pinned waist-cm inference and exact SHAP; no training or score changes."""
from pathlib import Path
import sys
import numpy as np
import pandas as pd

RELEASE_PIN = 'bdca54917705cde75fc2d1b275c49a91ebfef05f56f45472d29e4b97ba77c2e9'
FEATURES = ['age_years','sex_code','height_cm','weight_kg',
            'leisure_aerobic_moderate_equivalent_min_week','strength_days_week']
WRAPPER_VERSION = 'waist-cm-shap-v0.1-20260911'

class WaistCM:
    def __init__(self, release):
        release=Path(release).resolve()
        sys.path.insert(0,str(release))
        # Use a fresh Python process per release; don't mix src modules from projects.
        from src.tuntun_app_vnext.release import load_release
        self.app=load_release(release,RELEASE_PIN)
        self.predictor=self.app.peer_service.adapter.predictor
        from src.tuntun_peer.bundle import d0
        self.prepare_features=d0.prepare_features
        if list(self.predictor.waist.named_steps)!=['model']:
            raise ValueError('UNSUPPORTED_PIPELINE_FOR_TREE_SHAP')
        self.tree=self.predictor.waist.named_steps['model']

    def prepare(self, frame):
        if not isinstance(frame,pd.DataFrame):
            raise ValueError('ORDERED_DATAFRAME_REQUIRED')
        return self.prepare_features(frame)

    def predict_waist_cm(self, frame, *, pregnancy_status):
        if pregnancy_status!='nonpregnant':
            raise ValueError('PREGNANCY_UNSUPPORTED_OR_UNKNOWN')
        prepared=self.prepare(frame)
        result=np.asarray(self.predictor.waist.predict(prepared),dtype=float)
        if result.shape!=(len(frame),) or not np.isfinite(result).all():
            raise ValueError('INVALID_WAIST_OUTPUT')
        return result

    def explain_waist_cm(self, frame, background, *, pregnancy_status):
        import shap
        predictions=self.predict_waist_cm(frame,pregnancy_status=pregnancy_status)
        prepared=self.prepare(frame)
        bg=self.prepare(background)
        # Explicit maximum prevents the masker silently subsampling to 100.
        masker=shap.maskers.Independent(bg,max_samples=len(bg))
        def predict_masked(array):
            return self.predict_waist_cm(pd.DataFrame(array,columns=FEATURES),
                pregnancy_status=pregnancy_status)
        explainer=shap.ExactExplainer(predict_masked,masker,feature_names=FEATURES)
        if len(explainer.masker.data)!=len(bg):
            raise ValueError('BACKGROUND_ROW_COUNT_CHANGED')
        explanation=explainer(prepared,max_evals=64,batch_size=19200,silent=True)
        values=np.asarray(explanation.values,dtype=float)
        base=np.asarray(explanation.base_values,dtype=float).reshape(-1)
        reconstructed=base+values.sum(axis=1)
        np.testing.assert_allclose(reconstructed,predictions,rtol=0,atol=1e-9)
        mean_prediction=float(np.mean(self.predict_waist_cm(bg,pregnancy_status=pregnancy_status)))
        np.testing.assert_allclose(base,mean_prediction,rtol=0,atol=1e-9)
        return {'wrapperVersion':WRAPPER_VERSION,'output':'predicted_waist_cm','unit':'cm',
            'predictionCm':predictions.tolist(),'baseValueCm':base.tolist(),
            'shapValuesCm':values.tolist(),'featureOrder':FEATURES,
            'preparedInputs':prepared.to_dict(orient='records'),
            'backgroundRowsUsed':len(bg),'backgroundMeanPredictionCm':mean_prediction,
            'explainer':'ExactExplainer','masker':'Independent','link':'identity',
            'modelOutput':'predict_waist_cm','maxCoalitions':64,
            'maxAbsAdditivityErrorCm':float(np.max(np.abs(reconstructed-predictions))),
            'atolCm':1e-9,'shapVersion':shap.__version__}

def main():
    import argparse,json
    parser=argparse.ArgumentParser()
    parser.add_argument('--release',type=Path,default=Path(__file__).parent/'01_inference')
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--pregnancy-status',choices=['nonpregnant','pregnant','unknown'],required=True)
    parser.add_argument('--background',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    model=WaistCM(args.release)
    frame=pd.read_csv(args.input)
    if args.background:
        result=model.explain_waist_cm(frame,pd.read_csv(args.background),pregnancy_status=args.pregnancy_status)
    else:
        result={'output':'predicted_waist_cm','unit':'cm','wrapperVersion':WRAPPER_VERSION,
                'predictionCm':model.predict_waist_cm(frame,pregnancy_status=args.pregnancy_status).tolist()}
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    print('PASS: '+str(args.output))

if __name__=='__main__': main()
