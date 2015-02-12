package brickhouse.udf.collect;
/**
 * Copyright 2012 Klout, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/


/**
 *  Aggregate function to combine several
 *    lists together to return a list of unique values 
 */

import scala.collection.mutable.ArrayBuffer;
import scala.collection.mutable.Buffer;
import scala.collection.Seq;
import scala.collection.JavaConversions;
import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.AbstractGenericUDAFResolver;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.AbstractAggregationBuffer;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;

/**
 *  Aggregate function to combine several
 *    lists together to return a list of unique values 
 */


@Description(name="combine_unique",
value = "_FUNC_(x) - Returns an array of all distinct elements of all lists in the aggregation group " 
)
public class CombineUniqueUDAF extends AbstractGenericUDAFResolver {


	/// Snarfed from Hives CollectSet UDAF

	@Override
	public GenericUDAFEvaluator getEvaluator(TypeInfo[] parameters)
			throws SemanticException {
		if (parameters.length != 1) {
			throw new UDFArgumentTypeException(parameters.length - 1,
					"One argument is expected, taking an array as an argument");
		}
		if(! parameters[0].getCategory().equals( Category.LIST)) {
			throw new UDFArgumentTypeException(parameters.length - 1,
					"One argument is expected, taking an array as an argument");
		}
		return new CombineUniqueUDAFEvaluator();
	}

	public static class CombineUniqueUDAFEvaluator extends GenericUDAFEvaluator {
		private static final Logger LOG = Logger.getLogger( CombineUniqueUDAFEvaluator.class);
		// For PARTIAL1 and COMPLETE: ObjectInspectors for original data
		private ListObjectInspector inputOI;
		// For PARTIAL2 and FINAL: ObjectInspectors for partial aggregations (list
		// of objs)
		//private StandardListObjectInspector loi;
		///private StandardListObjectInspector internalMergeOI;


		static class UniqueSetBuffer extends AbstractAggregationBuffer {
			HashSet collectSet = new HashSet();
		}

    public ObjectInspector init(Mode m, ObjectInspector[] parameters)
        throws HiveException {
      super.init(m, parameters);
      inputOI = (ListObjectInspector) parameters[0];
      ObjectInspector elemInsp = PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(
          ((PrimitiveObjectInspector) (inputOI.getListElementObjectInspector())).getPrimitiveCategory());
      return ObjectInspectorFactory
          .getStandardListObjectInspector(elemInsp );
    }

		@Override
		public AbstractAggregationBuffer getNewAggregationBuffer() throws HiveException {
			AbstractAggregationBuffer buff= new UniqueSetBuffer();
			reset(buff);
			return buff;
		}

		public void iterate(AbstractAggregationBuffer agg, Object[] parameters)
				throws HiveException {
			Object p = parameters[0];

			if (p != null) {
				UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
				putIntoSet(p, myagg);
			}
		}
		@Override
		public void iterate(AggregationBuffer agg, Object[] parameters)
				throws HiveException {
		    Object p = parameters[0];

			if (p != null) {
				UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
				putIntoSet(p, myagg);
			}
		}

		public void merge(AbstractAggregationBuffer agg, Object partial)
				throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			putIntoSet( partial, myagg);
		}

		public void reset(AbstractAggregationBuffer buff) throws HiveException {
			UniqueSetBuffer arrayBuff = (UniqueSetBuffer) buff;
			arrayBuff.collectSet = new HashSet();
		}

		@Override
		public void merge(AggregationBuffer agg, Object partial)
				throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			putIntoSet( partial, myagg);
		}

		@Override
		public void reset(AggregationBuffer buff) throws HiveException {
			UniqueSetBuffer arrayBuff = (UniqueSetBuffer) buff;
			arrayBuff.collectSet = new HashSet();
		}

		public Object terminate(AbstractAggregationBuffer agg) throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			ArrayList<Object> ret = new ArrayList<Object>(myagg.collectSet.size());
			ret.addAll( myagg.collectSet );
			return ret;

		}

	        @Override
		public Object terminate(AggregationBuffer agg) throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			ArrayList<Object> ret = new ArrayList<Object>(myagg.collectSet.size());
			ret.addAll( myagg.collectSet );
			return ret;

		}


    private void putIntoSet(Object p, UniqueSetBuffer myagg) {
	ArrayList<Object> finalList = new ArrayList<Object>();
	scala.collection.mutable.ArrayBuffer b =  (scala.collection.mutable.ArrayBuffer) p;
	scala.collection.Iterator iter = b.toIterator();
	while(iter.hasNext()){
		Object o = iter.next();
		finalList.add(o);
	}

      ArrayList<Object> pList = (ArrayList<Object>) inputOI.getList(finalList);
      ObjectInspector objInsp = inputOI.getListElementObjectInspector();
      for( Object obj : pList) {
        Object realObj = ((PrimitiveObjectInspector)objInsp).getPrimitiveJavaObject( obj);
        myagg.collectSet.add( realObj);
      }
    }

		@Override
		public Object terminatePartial(AggregationBuffer agg) throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			ArrayList<Object> ret = new ArrayList<Object>(myagg.collectSet.size());
			ret.addAll(myagg.collectSet);
			return ret;
		}

		public Object terminatePartial(AbstractAggregationBuffer agg) throws HiveException {
			UniqueSetBuffer myagg = (UniqueSetBuffer) agg;
			ArrayList<Object> ret = new ArrayList<Object>(myagg.collectSet.size());
			ret.addAll(myagg.collectSet);
			return ret;
		}
	}


}
