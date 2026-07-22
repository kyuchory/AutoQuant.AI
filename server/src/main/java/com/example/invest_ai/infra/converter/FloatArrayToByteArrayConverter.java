package com.example.invest_ai.infra.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * float[] ↔ byte[] 변환기
 *
 * MySQL VECTOR(1536) 컬럼은 내부적으로 4바이트 float 리틀엔디언 배열로 저장되므로,
 * JPA Entity에서는 float[] 필드에 @Convert(converter = FloatArrayToByteArrayConverter.class)를
 * 선언하여 사용한다.
 *
 * 추후 RAG 단계에서 유사도 검색이 필요할 경우,
 * 네이티브 쿼리로 MySQL VECTOR_DISTANCE() 함수를 직접 호출한다.
 */
@Converter
public class FloatArrayToByteArrayConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(attribute.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : attribute) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[dbData.length / Float.BYTES];
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }
        return result;
    }
}