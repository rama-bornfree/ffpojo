package com.github.ffpojo.parser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.github.ffpojo.exception.FFPojoRuntimeException;
import com.github.ffpojo.exception.FieldDecoratorException;
import com.github.ffpojo.exception.RecordParserException;
import com.github.ffpojo.metadata.FieldDecorator;
import com.github.ffpojo.metadata.positional.PositionalFieldDescriptor;
import com.github.ffpojo.metadata.positional.PositionalRecordDescriptor;
import com.github.ffpojo.metadata.positional.annotation.AccessorType;
import com.github.ffpojo.util.ReflectUtil;
import com.github.ffpojo.util.StringUtil;



class PositionalRecordParser extends BaseRecordParser implements RecordParser {

	public PositionalRecordParser(PositionalRecordDescriptor recordDescriptor) {
		super(recordDescriptor);
	}
	
	//TODO: alterar de object pra classe correta
	public Object parseFromText(FileClassConfiguration fileClassConfiguration, String text)throws RecordParserException {
		return parseFromText(fileClassConfiguration.getClassByIdLine(text), text);
	}
	
	public <T> T parseFromText(Class<T> recordClazz, String text) throws RecordParserException {
		T record;
		try {
			record = recordClazz.newInstance();
		} catch (Exception e) {
			throw new RecordParserException("Error while instantiating record class, make sure that is provided a default constructor for class " + recordClazz, e);
		}
		
		List<PositionalFieldDescriptor> positionalFieldDescriptors = getRecordDescriptor().getFieldDescriptors();
		for (int i = 0; i < positionalFieldDescriptors.size(); i++) {
			PositionalFieldDescriptor actualFieldDescriptor = positionalFieldDescriptors.get(i);
			
			String fieldValue = "";
			int initialIndex = actualFieldDescriptor.getInitialPosition() - 1;
			int finalIndex = actualFieldDescriptor.getFinalPosition();
			if (text.length() < finalIndex) {
				if (!((PositionalRecordDescriptor)recordDescriptor).isIgnorePositionNotFound()){
					throw new RecordParserException("The text length is less than the declared length in field mapping: " + actualFieldDescriptor.getGetter());
				}
				if (text.length() >  initialIndex && initialIndex >=0){
					fieldValue =  text.substring(initialIndex);
				}
			} else {
				fieldValue = text.substring(initialIndex, finalIndex);
			}

			if (actualFieldDescriptor.isTrimOnRead()) {
				fieldValue = fieldValue.trim();
			}

			if (AccessorType.FIELD.equals(actualFieldDescriptor.getAccessorType())){
				Field field = actualFieldDescriptor.getField();
				field.setAccessible(true);
				try {
					Object value =  actualFieldDescriptor.getDecorator().fromString(fieldValue);
					field.set(record, value);
				} catch (Exception e) {
					throw new FFPojoRuntimeException(e);
				}
				
			}else{
				Method setter;
				Class<?> getterReturnType = actualFieldDescriptor.getGetter().getReturnType();
				try {
					setter = ReflectUtil.getSetterFromGetter(actualFieldDescriptor.getGetter(), new Class<?>[] {String.class}, recordClazz);
				} catch (NoSuchMethodException e1) {
					try {
						setter = ReflectUtil.getSetterFromGetter(actualFieldDescriptor.getGetter(), new Class<?>[] {getterReturnType}, recordClazz);
					} catch (NoSuchMethodException e2) {
						throw new RecordParserException("Compatible setter not found for getter " + actualFieldDescriptor.getGetter(), e2);
					}
				}
				Object parameter;
				try {
					FieldDecorator<?> decorator = actualFieldDescriptor.getDecorator();
					parameter = decorator.fromString(fieldValue);
					setter.invoke(record, parameter);
				} catch (FieldDecoratorException e) {
					throw new RecordParserException(e);
				} catch (Exception e) {
					throw new RecordParserException("Error while invoking setter method, make sure that is provided a compatible fromString decorator method: " + setter, e);
				}
			}
			
			
		}
		
		return record;
	}
	
	@SuppressWarnings("unchecked")
	public <T> String parseToText(T record) throws RecordParserException {
		StringBuffer sbufRecordLine = new StringBuffer();
		
		List<PositionalFieldDescriptor> positionalFieldDescriptors = getRecordDescriptor().getFieldDescriptors();
		for (int i = 0; i < positionalFieldDescriptors.size(); i++) {
			PositionalFieldDescriptor actualFieldDescriptor = positionalFieldDescriptors.get(i);
			
			boolean isFirstFieldDescriptor = i==0;
			PositionalFieldDescriptor previousFieldDescriptor = null;
			if (!isFirstFieldDescriptor) {
				previousFieldDescriptor = positionalFieldDescriptors.get(i-1);
			}
			Object fieldValueObj;
			if (AccessorType.FIELD.equals(actualFieldDescriptor.getAccessorType())){
				Field field = actualFieldDescriptor.getField();
				field.setAccessible(true);
				try {
					fieldValueObj = field.get(record);
				} catch (Exception e) {
					throw new RecordParserException("Error while reading value on field: " +  field.getName());
				}
			}else{				
				Method getter = actualFieldDescriptor.getGetter();
				try {
					fieldValueObj = getter.invoke(record, new Object[]{});
				} catch (Exception e) {
					throw new RecordParserException("Error while invoking getter method: " + getter, e);
				} 
			}
			
			String fieldValue;
			if (fieldValueObj == null) {
				fieldValue = "";
			} else {
				try {
					FieldDecorator<Object> decorator = (FieldDecorator<Object>)actualFieldDescriptor.getDecorator();
					fieldValue = decorator.toString(fieldValueObj);
				} catch (FieldDecoratorException e) {
					throw new RecordParserException(e);
				}
			}
			
			int fieldLength = actualFieldDescriptor.getFinalPosition() - actualFieldDescriptor.getInitialPosition() + 1;
			String sizedFieldValue = StringUtil.fillToLength(fieldValue, fieldLength, actualFieldDescriptor.getPaddingCharacter(), StringUtil.Direction.valueOf(actualFieldDescriptor.getPaddingAlign().toString()));
			
			// Check for blank spaces and fill
			if (isFirstFieldDescriptor && actualFieldDescriptor.getInitialPosition() > 1) {
				int blankSpaces = actualFieldDescriptor.getInitialPosition() - 1;
				sizedFieldValue = StringUtil.fillToLength(sizedFieldValue, blankSpaces + fieldLength, ' ', StringUtil.Direction.LEFT);
			} else if (!isFirstFieldDescriptor && previousFieldDescriptor.getFinalPosition() < actualFieldDescriptor.getInitialPosition() - 1) {
				int blankSpaces = actualFieldDescriptor.getInitialPosition() - previousFieldDescriptor.getFinalPosition() - 1;
				sizedFieldValue = StringUtil.fillToLength(sizedFieldValue, blankSpaces + fieldLength, ' ', StringUtil.Direction.LEFT);
			}
			sbufRecordLine.append(sizedFieldValue);
		}

		return sbufRecordLine.toString();
	}
	
	@Override
	protected PositionalRecordDescriptor getRecordDescriptor() {
		return (PositionalRecordDescriptor)recordDescriptor;
	}
	
}
